package br.edu.atitus.paymentapi;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.services.CheckoutService;
import br.edu.atitus.paymentapi.services.WebhookService;
import br.edu.atitus.paymentapi.support.FakeOrderApiClient;
import br.edu.atitus.paymentapi.support.FakePaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:",
        "payment.stripe.secret-key=sk_test_integration",
        "payment.stripe.webhook-secret=whsec_integration",
        "payment.stripe.success-url=http://localhost/ok?session_id={CHECKOUT_SESSION_ID}",
        "payment.stripe.cancel-url=http://localhost/cancel"
})
@Testcontainers(disabledWithoutDocker = true)
class PaymentIntegrationTest {

    private static final long ORDER_ID = 10L;
    private static final long CUSTOMER_ID = 3L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }

        @Bean
        @Primary
        FakeOrderApiClient fakeOrderApiClient() {
            return new FakeOrderApiClient();
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private WebhookService webhookService;
    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private FakeOrderApiClient orderApi;

    @BeforeEach
    void reset() {
        jdbc.update("delete from processed_stripe_event");
        jdbc.update("delete from payment");
        gateway.createdSessions.clear();
        gateway.refunds.clear();
        gateway.createDelayMs = 0;
        orderApi.orders.clear();
        orderApi.confirmCalls.clear();
        orderApi.cancelCalls.clear();
        orderApi.confirmStatus = "CONFIRMED";
        orderApi.failure = null;
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "149.90"));
    }

    @Test
    void uniqueIndexesAllowOnlyOneOpenPaymentPerOrderAndOneRowPerSession() {
        insertPayment(ORDER_ID, "CREATED", "cs_a");

        assertThrows(DataIntegrityViolationException.class, () -> insertPayment(ORDER_ID, "PENDING", "cs_b"));

        jdbc.update("update payment set status = 'FAILED' where stripe_checkout_session_id = 'cs_a'");
        insertPayment(ORDER_ID, "CREATED", "cs_c");

        assertThrows(DataIntegrityViolationException.class, () -> insertPayment(99L, "FAILED", "cs_c"));
    }

    @Test
    void concurrentCheckoutsForTheSameOrderCreateOnePayment() throws Exception {
        gateway.createDelayMs = 300;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    return checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            }));
        }
        start.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> result : results) {
            outcomes.add(result.get());
        }
        executor.shutdown();

        assertThat(count("payment")).isEqualTo(1);
        assertThat(gateway.createdSessions).hasSize(1);
        assertThat(outcomes).filteredOn(CheckoutSessionResponse.class::isInstance).isNotEmpty();
        assertThat(outcomes).allMatch(o -> o instanceof CheckoutSessionResponse || Integer.valueOf(409).equals(o));
    }

    @Test
    void duplicateEventIsProcessedOnce() {
        CheckoutSessionResponse checkout = checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);
        PaymentEvent event = paidEvent("evt_dup", checkout.paymentId());

        webhookService.process(event);
        webhookService.process(event);

        assertThat(orderApi.confirmCalls).containsExactly(ORDER_ID);
        assertThat(count("processed_stripe_event")).isEqualTo(1);
        assertThat(statusOf(checkout.paymentId())).isEqualTo("APPROVED");
    }

    @Test
    void orderApiDownRollsBackEverythingIncludingTheEvent() {
        CheckoutSessionResponse checkout = checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);
        orderApi.failure = new OrderApiException("order-api indisponível");

        assertThrows(OrderApiException.class, () -> webhookService.process(paidEvent("evt_down", checkout.paymentId())));

        assertThat(count("processed_stripe_event")).isZero();
        assertThat(statusOf(checkout.paymentId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select stripe_payment_intent_id from payment where id = ?",
                String.class, checkout.paymentId())).isNull();

        // Reenvio do Stripe depois que o order-api volta: processa normalmente.
        orderApi.failure = null;
        webhookService.process(paidEvent("evt_down", checkout.paymentId()));
        assertThat(statusOf(checkout.paymentId())).isEqualTo("APPROVED");
    }

    @Test
    void confirmNotFoundRollsBackAndDoesNotRefund() {
        CheckoutSessionResponse checkout = checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);
        orderApi.failure = new OrderApiException("order-api respondeu 404 ao confirmar o pedido 10.");

        assertThrows(OrderApiException.class, () -> webhookService.process(paidEvent("evt_404", checkout.paymentId())));

        assertThat(count("processed_stripe_event")).isZero();
        assertThat(statusOf(checkout.paymentId())).isEqualTo("PENDING");
        assertThat(gateway.refunds).isEmpty();
    }

    private PaymentEvent paidEvent(String eventId, Long paymentId) {
        return new PaymentEvent(eventId, "checkout.session.completed", PaymentEventType.CHECKOUT_COMPLETED,
                "cs_test_" + paymentId, paymentId, "pi_test_" + paymentId, true);
    }

    private void insertPayment(long orderId, String status, String sessionId) {
        jdbc.update("""
                insert into payment (order_id, customer_id, amount, currency, status,
                                     stripe_checkout_session_id, created_at, updated_at)
                values (?, ?, 10.00, 'BRL', ?, ?, now(), now())
                """, orderId, CUSTOMER_ID, status, sessionId);
    }

    private String statusOf(Long paymentId) {
        return jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
