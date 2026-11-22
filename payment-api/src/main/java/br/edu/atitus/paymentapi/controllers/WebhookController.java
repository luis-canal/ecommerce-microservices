package br.edu.atitus.paymentapi.controllers;

import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.services.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público (sem JWT) chamado pelo Stripe. Autenticado apenas pela assinatura.
 * O corpo é lido cru, como String, porque a assinatura é calculada sobre os bytes originais.
 * Nunca registra o payload nem a assinatura em log.
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentGateway paymentGateway;
    private final WebhookService webhookService;

    public WebhookController(PaymentGateway paymentGateway, WebhookService webhookService) {
        this.paymentGateway = paymentGateway;
        this.webhookService = webhookService;
    }

    @PostMapping("/payments/webhook")
    public ResponseEntity<?> receive(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature
    ) {
        PaymentEvent event;
        try {
            event = paymentGateway.parseWebhookEvent(payload, signature);
        } catch (InvalidWebhookSignatureException exception) {
            log.warn("Webhook rejeitado: {}", exception.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                    .body("Assinatura ou evento do webhook inválido.");
        }

        try {
            webhookService.process(event);
        } catch (RuntimeException exception) {
            log.error("Falha ao processar o evento {} ({}); será reenviado pelo Stripe: {}",
                    event.eventId(), event.rawType(), exception.getMessage());
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                    .body("Não foi possível processar o webhook. O evento será reenviado.");
        }
        return ResponseEntity.ok().build();
    }
}
