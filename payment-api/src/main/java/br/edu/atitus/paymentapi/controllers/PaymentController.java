package br.edu.atitus.paymentapi.controllers;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.dtos.PaymentResponse;
import br.edu.atitus.paymentapi.services.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ws/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final CheckoutService checkoutService;

    public PaymentController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /** Corpo vazio: valor e moeda vêm sempre do order-api. */
    @PostMapping("/orders/{orderId}/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(checkoutService.startCheckout(orderId, userId));
    }

    /** Status do pagamento mais recente do pedido. O frontend consulta; nunca define o status. */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> findPayment(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(checkoutService.findLatest(orderId, userId));
    }

    @ExceptionHandler(OrderApiException.class)
    public ProblemDetail handleOrderApiFailure(OrderApiException exception) {
        log.warn("Falha ao consultar o order-api: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Serviço de pedidos indisponível.");
    }

    private void requireCustomer(Integer userType) {
        if (userType == null || userType != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente clientes podem pagar pedidos.");
        }
    }
}
