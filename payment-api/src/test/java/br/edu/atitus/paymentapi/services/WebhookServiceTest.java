package br.edu.atitus.paymentapi.services;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.repositories.PaymentRepository;
import br.edu.atitus.paymentapi.repositories.ProcessedStripeEventRepository;
import br.edu.atitus.paymentapi.support.FakeOrderApiClient;
import br.edu.atitus.paymentapi.support.FakePaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ProcessedStripeEventRepository eventRepository = mock(ProcessedStripeEventRepository.class);
    private final FakeOrderApiClient orderApi = new FakeOrderApiClient();
    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final WebhookService service =
            new WebhookService(paymentRepository, eventRepository, orderApi, gateway);

    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        payment = CheckoutServiceTest.pendingPayment(5L);
        when(eventRepository.insertIfAbsent(anyString(), anyString())).thenReturn(1);
        when(paymentRepository.lockBySessionId("cs_test_5")).thenReturn(Optional.of(payment));
        when(paymentRepository.lockBySessionId("cs_unknown")).thenReturn(Optional.empty());
        when(paymentRepository.lockById(any())).thenReturn(Optional.empty());
    }

    @Test
    void paidCheckoutConfirmsOrderAndApproves() {
        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true));

        assertThat(orderApi.confirmCalls).containsExactly(10L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getConfirmedAt()).isNotNull();
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_1");
        assertThat(gateway.refunds).isEmpty();
    }

    @Test
    void unpaidCheckoutStaysPendingUntilAsyncResult() {
        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderApi.confirmCalls).isEmpty();

        service.process(event(PaymentEventType.ASYNC_PAYMENT_SUCCEEDED, "cs_test_5", true));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void duplicateEventIsProcessedOnce() {
        when(eventRepository.insertIfAbsent("evt_checkout.session.completed_true", "checkout.session.completed")).thenReturn(1).thenReturn(0);
        PaymentEvent event = event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true);

        service.process(event);
        service.process(event);

        assertThat(orderApi.confirmCalls).containsExactly(10L);
    }

    @Test
    void orderReturningExpiredOnConfirmRefundsLatePayment() {
        orderApi.confirmStatus = "EXPIRED";

        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true));

        assertThat(gateway.refunds).containsExactly("pi_test_1|refund-5");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getConfirmedAt()).isNull();
    }

    @Test
    void orderConflictOnConfirmRefundsLatePayment() {
        orderApi.confirmStatus = null;

        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true));

        assertThat(gateway.refunds).containsExactly("pi_test_1|refund-5");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void orderApiFailureOnConfirmPropagatesWithoutChangingPayment() {
        orderApi.failure = new OrderApiException("order-api indisponível");

        assertThrows(OrderApiException.class,
                () -> service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true)));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(gateway.refunds).isEmpty();
    }

    @Test
    void orderNotFoundOnConfirmIsAnErrorAndDoesNotRefund() {
        // O cliente HTTP converte 404 do confirm em OrderApiException (ver RestOrderApiClientTest).
        orderApi.failure = new OrderApiException("order-api respondeu 404 ao confirmar o pedido 10.");

        assertThrows(OrderApiException.class,
                () -> service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true)));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(gateway.refunds).isEmpty();
    }

    @Test
    void expiredSessionMarksExpiredAndCancelsOrder() {
        service.process(event(PaymentEventType.SESSION_EXPIRED, "cs_test_5", false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(orderApi.cancelCalls).containsExactly(10L);
    }

    @Test
    void asyncFailureMarksFailedAndCancelsOrder() {
        service.process(event(PaymentEventType.ASYNC_PAYMENT_FAILED, "cs_test_5", false));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderApi.cancelCalls).containsExactly(10L);
    }

    @Test
    void lateEventOnFinalStateIsIgnored() {
        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true));

        service.process(event(PaymentEventType.SESSION_EXPIRED, "cs_test_5", false));
        service.process(event(PaymentEventType.ASYNC_PAYMENT_SUCCEEDED, "cs_test_5", true));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderApi.confirmCalls).hasSize(1);
        assertThat(orderApi.cancelCalls).isEmpty();
    }

    @Test
    void approvalOnClosedPaymentIsRefundedWithoutConfirmingOrder() {
        for (PaymentEventType closing : new PaymentEventType[]{
                PaymentEventType.ASYNC_PAYMENT_FAILED, PaymentEventType.SESSION_EXPIRED, null}) {
            PaymentEntity closed = CheckoutServiceTest.pendingPayment(5L);
            when(paymentRepository.lockBySessionId("cs_test_5")).thenReturn(Optional.of(closed));
            if (closing == null) {
                closed.transitionTo(PaymentStatus.CANCELLED);
            } else {
                service.process(event(closing, "cs_test_5", false));
            }
            gateway.refunds.clear();

            service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_test_5", true));

            assertThat(closed.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(gateway.refunds).containsExactly("pi_test_1|refund-5");
        }
        assertThat(orderApi.confirmCalls).isEmpty();
    }

    @Test
    void unknownSessionIsIgnored() {
        service.process(event(PaymentEventType.CHECKOUT_COMPLETED, "cs_unknown", true));

        assertThat(orderApi.confirmCalls).isEmpty();
        verify(paymentRepository).lockById(5L);
    }

    @Test
    void ignoredEventTypeIsOnlyRecorded() {
        service.process(new PaymentEvent("evt_x", "charge.updated", PaymentEventType.IGNORED,
                null, null, null, false));

        verify(eventRepository).insertIfAbsent("evt_x", "charge.updated");
        verify(paymentRepository, never()).lockBySessionId(any());
    }

    private static PaymentEvent event(PaymentEventType type, String sessionId, boolean paid) {
        String rawType = switch (type) {
            case CHECKOUT_COMPLETED -> "checkout.session.completed";
            case ASYNC_PAYMENT_SUCCEEDED -> "checkout.session.async_payment_succeeded";
            case ASYNC_PAYMENT_FAILED -> "checkout.session.async_payment_failed";
            case SESSION_EXPIRED -> "checkout.session.expired";
            case IGNORED -> "other";
        };
        String eventId = "evt_" + rawType + "_" + paid;
        return new PaymentEvent(eventId, rawType, type, sessionId, 5L, "pi_test_1", paid);
    }
}
