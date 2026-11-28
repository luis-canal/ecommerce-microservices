package br.edu.atitus.paymentapi.services;

import br.edu.atitus.paymentapi.clients.OrderApiClient;
import br.edu.atitus.paymentapi.clients.OrderPaymentInfo;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.dtos.PaymentResponse;
import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.CheckoutSession;
import br.edu.atitus.paymentapi.provider.CheckoutSessionRequest;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.provider.PaymentProviderException;
import br.edu.atitus.paymentapi.repositories.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Inicia o pagamento de um pedido. Valor e moeda vêm sempre do order-api.
 *
 * Sem transação longa: o pagamento CREATED é gravado antes da chamada ao provedor
 * (o índice único parcial impede dois pagamentos abertos para o mesmo pedido) e
 * atualizado depois dela.
 *
 * Quando o provedor já conhece o resultado (modo simulado), ele é processado em seguida pelo
 * {@link WebhookService}, com as mesmas transições, confirmação do pedido e deduplicação.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    /** Um CREATED mais antigo que isto foi abandonado (a chamada ao provedor já teria terminado). */
    static final Duration STALE_CREATED_AFTER = Duration.ofMinutes(2);

    private final PaymentRepository paymentRepository;
    private final OrderApiClient orderApiClient;
    private final PaymentGateway paymentGateway;
    private final WebhookService webhookService;
    private final Duration minReservation;

    public CheckoutService(
            PaymentRepository paymentRepository,
            OrderApiClient orderApiClient,
            PaymentGateway paymentGateway,
            WebhookService webhookService,
            @Value("${payment.min-reservation-seconds:120}") long minReservationSeconds
    ) {
        this.paymentRepository = paymentRepository;
        this.orderApiClient = orderApiClient;
        this.paymentGateway = paymentGateway;
        this.webhookService = webhookService;
        this.minReservation = Duration.ofSeconds(minReservationSeconds);
    }

    public CheckoutSessionResponse startCheckout(Long orderId, Long customerId) {
        OrderPaymentInfo order = loadOwnedOrder(orderId, customerId);

        Optional<PaymentEntity> open = paymentRepository.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN);
        if (open.isPresent()) {
            Optional<CheckoutSessionResponse> reused = reuseOrDiscard(open.get(), order);
            if (reused.isPresent()) {
                return reused.get();
            }
        }

        requireNewSessionAllowed(order);

        PaymentEntity payment;
        try {
            payment = paymentRepository.saveAndFlush(new PaymentEntity(
                    orderId, customerId, order.totalAmount(), order.currency(), order.reservationExpiresAt()));
        } catch (DataIntegrityViolationException exception) {
            // Outra requisição abriu um pagamento para este pedido ao mesmo tempo.
            return paymentRepository.findFirstByOrderIdAndStatusIn(orderId, PaymentStatus.OPEN)
                    .filter(existing -> existing.getStatus() == PaymentStatus.PENDING)
                    .map(this::respond)
                    .orElseThrow(() -> conflict("O pagamento deste pedido já está sendo iniciado; tente novamente."));
        }

        // Outra requisição pode ter aprovado um pagamento depois da consulta ao order-api acima.
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)) {
            payment.transitionTo(PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
            throw conflict("O pedido não está aguardando pagamento.");
        }

        CheckoutSession session;
        try {
            session = paymentGateway.createCheckoutSession(new CheckoutSessionRequest(
                    payment.getId(), orderId, customerId, payment.getAmount(), payment.getCurrency()));
        } catch (RuntimeException exception) {
            log.warn("Falha ao criar a sessão de checkout do pagamento {} (pedido {}): {}",
                    payment.getId(), orderId, exception.getMessage());
            payment.transitionTo(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível iniciar o pagamento.");
        }

        payment.attachCheckoutSession(session.sessionId(), session.url());
        PaymentEntity saved = paymentRepository.save(payment);
        log.info("Pagamento {} aberto para o pedido {}.", saved.getId(), orderId);
        return respond(saved);
    }

    public PaymentResponse findLatest(Long orderId, Long customerId) {
        return paymentRepository.findFirstByOrderIdAndCustomerIdOrderByIdDesc(orderId, customerId)
                .map(PaymentResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));
    }

    /** Resposta para um pagamento PENDING, concluindo-o antes se o resultado já for conhecido. */
    private CheckoutSessionResponse respond(PaymentEntity pending) {
        return immediateResult(pending)
                .map(result -> complete(pending.getId(), result))
                .orElseGet(() -> CheckoutSessionResponse.fromEntity(pending));
    }

    private Optional<PaymentEvent> immediateResult(PaymentEntity payment) {
        return paymentGateway.immediateResult(payment.getStripeCheckoutSessionId(), payment.getId());
    }

    /**
     * Processa o resultado pelo caminho do webhook. Se o order-api falhar, nada é gravado, o
     * pagamento continua PENDING e uma nova chamada ao checkout tenta de novo.
     */
    private CheckoutSessionResponse complete(Long paymentId, PaymentEvent result) {
        webhookService.process(result);
        return paymentRepository.findById(paymentId)
                .map(CheckoutSessionResponse::fromEntity)
                .orElseThrow(() -> new IllegalStateException("Pagamento " + paymentId + " não encontrado."));
    }

    private OrderPaymentInfo loadOwnedOrder(Long orderId, Long customerId) {
        return orderApiClient.getPaymentInfo(orderId)
                .filter(order -> customerId.equals(order.customerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    /**
     * Devolve o pagamento aberto quando ele ainda serve. Se o pedido não está mais pendente ou
     * a reserva tem menos de {@code payment.min-reservation-seconds}, expira a sessão, encerra o
     * pagamento (CANCELLED) e devolve vazio; as regras de uma sessão nova decidem em seguida
     * (com reserva curta, a resposta é 409).
     */
    private Optional<CheckoutSessionResponse> reuseOrDiscard(PaymentEntity existing, OrderPaymentInfo order) {
        if (existing.getStatus() == PaymentStatus.CREATED) {
            if (existing.getCreatedAt().isAfter(Instant.now().minus(STALE_CREATED_AFTER))) {
                throw conflict("O pagamento deste pedido já está sendo iniciado; tente novamente.");
            }
            existing.transitionTo(PaymentStatus.CANCELLED);
            paymentRepository.save(existing);
            return Optional.empty();
        }

        // Mesma regra de uma sessão nova: reserva com tempo suficiente para o cliente pagar.
        boolean orderStillPayable = isPendingPayment(order)
                && order.reservationExpiresAt() != null
                && order.reservationExpiresAt().isAfter(Instant.now().plus(minReservation));
        // Com resultado imediato, um pedido já CONFIRMED indica que a confirmação aconteceu e só
        // o registro local falhou: o pagamento é concluído em vez de cancelado.
        Optional<PaymentEvent> result = immediateResult(existing);
        if (result.isPresent() && (orderStillPayable || "CONFIRMED".equals(order.status()))) {
            return Optional.of(complete(existing.getId(), result.get()));
        }
        if (orderStillPayable) {
            return Optional.of(CheckoutSessionResponse.fromEntity(existing));
        }

        boolean expired;
        try {
            expired = paymentGateway.expireSession(existing.getStripeCheckoutSessionId());
        } catch (PaymentProviderException exception) {
            log.warn("Falha ao expirar a sessão do pagamento {}: {}", existing.getId(), exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível encerrar o pagamento anterior.");
        }
        if (!expired) {
            // O cliente já concluiu o checkout: o resultado chegará pelo webhook.
            throw conflict("O pagamento anterior deste pedido está em processamento.");
        }
        existing.transitionTo(PaymentStatus.CANCELLED);
        paymentRepository.save(existing);
        log.info("Pagamento {} cancelado: o pedido {} não aceita mais esse checkout ou a reserva está no fim.",
                existing.getId(), order.id());
        return Optional.empty();
    }

    private void requireNewSessionAllowed(OrderPaymentInfo order) {
        if (!isPendingPayment(order)) {
            throw conflict("O pedido não está aguardando pagamento.");
        }
        if (order.reservationExpiresAt() == null
                || order.reservationExpiresAt().isBefore(Instant.now().plus(minReservation))) {
            throw conflict("A reserva do pedido está expirando; não é possível iniciar o pagamento.");
        }
    }

    private static boolean isPendingPayment(OrderPaymentInfo order) {
        return "PENDING_PAYMENT".equals(order.status());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
