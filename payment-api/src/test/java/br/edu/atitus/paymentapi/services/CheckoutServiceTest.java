package br.edu.atitus.paymentapi.services;

import br.edu.atitus.paymentapi.clients.OrderPaymentInfo;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.repositories.PaymentRepository;
import br.edu.atitus.paymentapi.support.FakeOrderApiClient;
import br.edu.atitus.paymentapi.support.FakePaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutServiceTest {

    private static final long ORDER_ID = 10L;
    private static final long CUSTOMER_ID = 3L;

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final FakeOrderApiClient orderApi = new FakeOrderApiClient();
    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final AtomicLong ids = new AtomicLong(100);
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(repository, orderApi, gateway, 120);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", ids.incrementAndGet());
            ReflectionTestUtils.setField(payment, "createdAt", Instant.now());
            return payment;
        });
        when(repository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void amountAndCurrencyComeFromOrderApi() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "149.90"));

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.test/cs_test_" + response.paymentId());
        assertThat(gateway.createdSessions).singleElement().satisfies(request -> {
            assertThat(request.amount()).isEqualByComparingTo("149.90");
            assertThat(request.currency()).isEqualTo("BRL");
            assertThat(request.paymentId()).isEqualTo(response.paymentId());
            assertThat(request.customerId()).isEqualTo(CUSTOMER_ID);
        });
    }

    @Test
    void orderOfAnotherCustomerOrMissingIsNotFound() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, 99L, "10.00"));

        ResponseStatusException otherCustomer = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(77L, CUSTOMER_ID));

        assertThat(otherCustomer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gateway.createdSessions).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void orderNotPendingPaymentIsConflict() {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, "CONFIRMED",
                new BigDecimal("10.00"), "BRL", Instant.now().plusSeconds(600)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gateway.createdSessions).isEmpty();
    }

    @Test
    void reservationAboutToExpireIsConflict() {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, "PENDING_PAYMENT",
                new BigDecimal("10.00"), "BRL", Instant.now().plusSeconds(60)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gateway.createdSessions).isEmpty();
    }

    @Test
    void openPaymentReturnsSameUrlWithoutCallingProvider() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "10.00"));
        PaymentEntity open = pendingPayment(5L);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(Optional.of(open));

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.paymentId()).isEqualTo(5L);
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.test/cs_test_5");
        assertThat(gateway.createdSessions).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void openPaymentOfExpiredReservationIsCancelledAtProvider() {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, "EXPIRED",
                new BigDecimal("10.00"), "BRL", Instant.now().minusSeconds(30)));
        PaymentEntity open = pendingPayment(5L);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(Optional.of(open));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(gateway.expiredSessions).containsExactly("cs_test_5");
        assertThat(open.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gateway.createdSessions).isEmpty();
    }

    @Test
    void openPaymentWithReservationAboutToExpireIsCancelledAtProviderAndConflict() {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, "PENDING_PAYMENT",
                new BigDecimal("10.00"), "BRL", Instant.now().plusSeconds(60)));
        PaymentEntity open = pendingPayment(5L);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(Optional.of(open));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gateway.expiredSessions).containsExactly("cs_test_5");
        assertThat(open.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(gateway.createdSessions).isEmpty();
    }

    @Test
    void openPaymentAlreadyCompletedAtProviderIsNotCancelled() {
        orderApi.orders.put(ORDER_ID, new OrderPaymentInfo(ORDER_ID, CUSTOMER_ID, "PENDING_PAYMENT",
                new BigDecimal("10.00"), "BRL", Instant.now().minusSeconds(30)));
        PaymentEntity open = pendingPayment(5L);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(Optional.of(open));
        gateway.expireSucceeds = false;

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(open.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void providerFailureMarksPaymentFailedAndReturnsBadGateway() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "10.00"));
        gateway.failOnCreate = true;

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.startCheckout(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ArgumentCaptor<PaymentEntity> saved = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void concurrentInsertReturnsTheOtherOpenPayment() {
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "10.00"));
        PaymentEntity winner = pendingPayment(5L);
        when(repository.findFirstByOrderIdAndStatusIn(eq(ORDER_ID), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any(PaymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_payment_open_per_order"));

        CheckoutSessionResponse response = service.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.paymentId()).isEqualTo(5L);
        assertThat(gateway.createdSessions).isEmpty();
    }

    @Test
    void latestPaymentOfAnotherCustomerIsNotFound() {
        when(repository.findFirstByOrderIdAndCustomerIdOrderByIdDesc(ORDER_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findLatest(ORDER_ID, CUSTOMER_ID));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    static PaymentEntity pendingPayment(long id) {
        PaymentEntity payment = new PaymentEntity(ORDER_ID, CUSTOMER_ID, new BigDecimal("10.00"), "BRL",
                Instant.now().plusSeconds(600));
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "createdAt", Instant.now());
        payment.attachCheckoutSession("cs_test_" + id, "https://checkout.stripe.test/cs_test_" + id);
        return payment;
    }
}
