package br.edu.atitus.paymentapi.services;

import br.edu.atitus.paymentapi.clients.OrderApiClient;
import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.repositories.PaymentRepository;
import br.edu.atitus.paymentapi.repositories.ProcessedStripeEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processa as notificações já validadas do provedor.
 *
 * Tudo acontece em uma transação: o registro do evento, a mudança de estado e as chamadas
 * ao order-api/provedor. Se algo falhar, a exceção desfaz tudo (inclusive o registro do
 * evento) e o webhook responde 500 para o provedor reenviar. As chamadas externas são
 * idempotentes, então o reenvio é seguro.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedStripeEventRepository eventRepository;
    private final OrderApiClient orderApiClient;
    private final PaymentGateway paymentGateway;

    public WebhookService(
            PaymentRepository paymentRepository,
            ProcessedStripeEventRepository eventRepository,
            OrderApiClient orderApiClient,
            PaymentGateway paymentGateway
    ) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.orderApiClient = orderApiClient;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public void process(PaymentEvent event) {
        if (eventRepository.insertIfAbsent(event.eventId(), event.rawType()) == 0) {
            log.info("Evento {} ({}) já processado; ignorado.", event.eventId(), event.rawType());
            return;
        }
        if (event.type() == PaymentEventType.IGNORED) {
            return;
        }

        Optional<PaymentEntity> found = findPayment(event);
        if (found.isEmpty()) {
            log.warn("Evento {} ({}) sem pagamento correspondente; ignorado.", event.eventId(), event.rawType());
            return;
        }
        PaymentEntity payment = found.get();
        if (payment.getStripePaymentIntentId() == null && event.paymentIntentId() != null) {
            payment.setStripePaymentIntentId(event.paymentIntentId());
        }

        switch (event.type()) {
            case CHECKOUT_COMPLETED -> {
                if (event.paid()) {
                    approve(payment);
                } else {
                    log.info("Pagamento {} concluído no checkout, aguardando confirmação assíncrona.", payment.getId());
                }
            }
            case ASYNC_PAYMENT_SUCCEEDED -> approve(payment);
            case ASYNC_PAYMENT_FAILED -> close(payment, PaymentStatus.FAILED);
            case SESSION_EXPIRED -> close(payment, PaymentStatus.EXPIRED);
            default -> {
            }
        }
    }

    private Optional<PaymentEntity> findPayment(PaymentEvent event) {
        Optional<PaymentEntity> bySession = event.sessionId() == null
                ? Optional.empty()
                : paymentRepository.lockBySessionId(event.sessionId());
        if (bySession.isPresent() || event.paymentId() == null) {
            return bySession;
        }
        return paymentRepository.lockById(event.paymentId());
    }

    /**
     * Confirma o pedido e só então aprova o pagamento. Se o pedido não puder mais ser
     * confirmado (reserva expirada, cancelado), o pagamento chegou tarde: é estornado.
     */
    private void approve(PaymentEntity payment) {
        if (!payment.getStatus().canTransitionTo(PaymentStatus.APPROVED)) {
            if (payment.getStatus().canTransitionTo(PaymentStatus.REFUNDED)) {
                // FAILED, EXPIRED ou CANCELLED: o dinheiro foi capturado, mas o pedido não será honrado.
                log.error("Pagamento {} recebeu aprovação já encerrado como {}; o valor será estornado.",
                        payment.getId(), payment.getStatus());
                refund(payment);
                return;
            }
            log.info("Pagamento {} já está {}; aprovação ignorada.", payment.getId(), payment.getStatus());
            return;
        }

        String orderStatus = orderApiClient.confirm(payment.getOrderId());
        if ("CONFIRMED".equals(orderStatus)) {
            payment.transitionTo(PaymentStatus.APPROVED);
            log.info("Pagamento {} aprovado; pedido {} confirmado.", payment.getId(), payment.getOrderId());
            return;
        }

        log.warn("Pagamento {} aprovado tardiamente: pedido {} está {}. Estornando.",
                payment.getId(), payment.getOrderId(), orderStatus == null ? "em conflito (409)" : orderStatus);
        refund(payment);
    }

    private void refund(PaymentEntity payment) {
        if (payment.getStripePaymentIntentId() == null) {
            throw new IllegalStateException("Pagamento " + payment.getId() + " sem transação para estornar.");
        }
        paymentGateway.refund(payment.getStripePaymentIntentId(), "refund-" + payment.getId());
        payment.transitionTo(PaymentStatus.REFUNDED);
    }

    /** Pagamento recusado ou sessão expirada: encerra o pagamento e libera a reserva do pedido. */
    private void close(PaymentEntity payment, PaymentStatus target) {
        if (!payment.getStatus().canTransitionTo(target)) {
            log.info("Pagamento {} já está {}; evento {} ignorado.", payment.getId(), payment.getStatus(), target);
            return;
        }
        payment.transitionTo(target);
        orderApiClient.cancel(payment.getOrderId());
        log.info("Pagamento {} marcado {}; pedido {} cancelado.", payment.getId(), target, payment.getOrderId());
    }
}
