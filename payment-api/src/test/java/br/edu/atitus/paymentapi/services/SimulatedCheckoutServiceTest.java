package br.edu.atitus.paymentapi.services;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.clients.OrderPaymentInfo;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.simulated.SimulatedPaymentGateway;
import br.edu.atitus.paymentapi.repositories.PaymentRepository;
import br.edu.atitus.paymentapi.repositories.ProcessedStripeEventRepository;
import br.edu.atitus.paymentapi.support.FakeOrderApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checkout no modo simulado, com o gateway e o WebhookService reais. */
class SimulatedCheckoutServiceTest {

    private static final long ORDER_ID = 10L;
    private static final long CUSTOMER_ID = 3L;

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final ProcessedStripeEventRepository events = mock(ProcessedStripeEventRepository.class);
    private final FakeOrderApiClient orderApi = new FakeOrderApiClient();
    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway("", "");
    private final List<PaymentEntity> payments = new ArrayList<>();
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        WebhookService webhookService = new WebhookService(repository, events, orderApi, gateway);
        service = new CheckoutService(repository, orderApi, gateway, webhookService, 120);

        when(events.insertIfAbsent(anyString(), anyString())).thenReturn(1);
        when(repository.saveAndFlush(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 100L + payments.size());
            ReflectionTestUtils.setField(payment, "createdAt", Instant.now());
            payments.add(payment);
            return payment;
        });
        when(repository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findById(any())).thenAnswer(invocation -> byId(invocation.getArgument(0)));
        when(repository.lockById(any())).thenAnswer(invocation -> byId(invocation.getArgument(0)));
        when(repository.lockBySessionId(anyString())).thenAnswer(invocation -> payments.stream()
                .filter(payment -> invocation.getArgument(0).equals(payment.getStripeCheckoutSessionId()))
                .findFirst());
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenAnswer(invocation -> {
            Collection<PaymentStatus> statuses = invocation.getArgument(1);
            return payments.stream().filter(payment -> statuses.contains(payment.getStatus())).findFirst();
        });
        when(repository.existsByOrderIdAndStatus(eq(ORDER_ID), any())).thenAnswer(invocation -> payments.stream()
                .anyMatch(payment -> payment.getStatus() == invocation.getArgument(1)));
    }

    @Test
    void checkoutIsApprovedImmediatelyAndConfirmsTheOrder() {
        order("PENDING_PAYMENT", 600);

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.provider()).isEqualTo("simulated");
        assertThat(response.checkoutUrl())
                .isEqualTo("http://localhost:8080/payments/return/success?session_id=sim_100&order_id=10");
        assertThat(orderApi.confirmCalls).containsExactly(ORDER_ID);
        assertThat(payments).singleElement().satisfies(payment -> {
            assertThat(payment.getStripeCheckoutSessionId()).isEqualTo("sim_100");
            assertThat(payment.getStripePaymentIntentId()).isEqualTo("sim_pi_100");
            assertThat(payment.getConfirmedAt()).isNotNull();
        });
    }

    @Test
    void orderRulesAreTheSameAsWithStripe() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, 99L, "10.00"));
        assertStatus(HttpStatus.NOT_FOUND);

        order("CONFIRMED", 600);
        assertStatus(HttpStatus.CONFLICT);

        order("PENDING_PAYMENT", 60);
        assertStatus(HttpStatus.CONFLICT);

        assertThat(payments).isEmpty();
        assertThat(orderApi.confirmCalls).isEmpty();
    }

    @Test
    void orderApiFailureKeepsPaymentPendingAndRetryApprovesTheSameOne() {
        order("PENDING_PAYMENT", 600);
        orderApi.failure = new OrderApiException("order-api indisponível");

        assertThrows(OrderApiException.class, () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));
        assertThat(payments).singleElement()
                .extracting(PaymentEntity::getStatus).isEqualTo(PaymentStatus.PENDING);

        orderApi.failure = null;
        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.paymentId()).isEqualTo(100L);
        assertThat(payments).hasSize(1);
    }

    @Test
    void pendingPaymentOfAnAlreadyConfirmedOrderIsApprovedNotCancelled() {
        order("CONFIRMED", -30);
        PaymentEntity pending = existingPayment("sim_5", 5L);

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void pendingPaymentOfAnExpiredOrderIsCancelled() {
        order("EXPIRED", -30);
        PaymentEntity pending = existingPayment("sim_5", 5L);

        assertStatus(HttpStatus.CONFLICT);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderApi.confirmCalls).isEmpty();
    }

    @Test
    void openStripePaymentLeftFromTheOtherModeIsNotApproved() {
        order("PENDING_PAYMENT", 600);
        PaymentEntity pending = existingPayment("cs_test_5", 5L);

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.provider()).isEqualTo("stripe");
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderApi.confirmCalls).isEmpty();
    }

    @Test
    void orderThatCannotBeConfirmedEndsRefunded() {
        order("PENDING_PAYMENT", 600);
        orderApi.confirmStatus = "EXPIRED";

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void secondApprovalForTheSameOrderIsRefused() {
        // O order-api respondeu PENDING_PAYMENT antes de outra requisição aprovar o pagamento.
        order("PENDING_PAYMENT", 600);
        service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertStatus(HttpStatus.CONFLICT);

        assertThat(payments).extracting(PaymentEntity::getStatus)
                .containsExactly(PaymentStatus.APPROVED, PaymentStatus.CANCELLED);
        assertThat(orderApi.confirmCalls).containsExactly(ORDER_ID);
    }

    private void order(String status, long reservationSeconds) {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, status,
                new BigDecimal("10.00"), "BRL", Instant.now().plusSeconds(reservationSeconds)));
    }

    private PaymentEntity existingPayment(String sessionId, long id) {
        PaymentEntity payment = CheckoutServiceTest.pendingPayment(id);
        ReflectionTestUtils.setField(payment, "stripeCheckoutSessionId", sessionId);
        payments.add(payment);
        return payment;
    }

    private Optional<PaymentEntity> byId(Long id) {
        return payments.stream().filter(payment -> id.equals(payment.getId())).findFirst();
    }

    private void assertStatus(HttpStatus expected) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));
        assertThat(exception.getStatusCode()).isEqualTo(expected);
    }
}
