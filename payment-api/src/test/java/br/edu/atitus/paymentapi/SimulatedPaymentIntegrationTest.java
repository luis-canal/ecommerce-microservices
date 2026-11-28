package br.edu.atitus.paymentapi;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.controllers.WebhookController;
import br.edu.atitus.paymentapi.dtos.CheckoutSessionResponse;
import br.edu.atitus.paymentapi.dtos.PaymentResponse;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.provider.simulated.SimulatedPaymentGateway;
import br.edu.atitus.paymentapi.provider.stripe.StripePaymentGateway;
import br.edu.atitus.paymentapi.services.CheckoutService;
import br.edu.atitus.paymentapi.support.FakeOrderApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contexto completo com provider=simulated e todas as variáveis do Stripe vazias. */
@SpringBootTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:",
        "payment.provider=simulated",
        "payment.stripe.secret-key=",
        "payment.stripe.webhook-secret=",
        "payment.stripe.success-url=",
        "payment.stripe.cancel-url="
})
@Testcontainers(disabledWithoutDocker = true)
class SimulatedPaymentIntegrationTest {

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
        FakeOrderApiClient fakeOrderApiClient() {
            return new FakeOrderApiClient();
        }
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private WebApplicationContext webContext;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private WebhookController webhookController;
    @Autowired
    private FakeOrderApiClient orderApi;

    @BeforeEach
    void reset() {
        jdbc.update("delete from processed_stripe_event");
        jdbc.update("delete from payment");
        orderApi.orders.clear();
        orderApi.confirmCalls.clear();
        orderApi.cancelCalls.clear();
        orderApi.confirmStatus = "CONFIRMED";
        orderApi.failure = null;
        orderApi.orders.put(ORDER_ID, FakeOrderApiClient.pendingOrder(ORDER_ID, CUSTOMER_ID, "149.90"));
    }

    @Test
    void contextStartsWithOnlyTheSimulatedGateway() {
        assertThat(context.getBeansOfType(PaymentGateway.class).values())
                .singleElement().isInstanceOf(SimulatedPaymentGateway.class);
        assertThat(context.getBeanNamesForType(StripePaymentGateway.class)).isEmpty();
    }

    @Test
    void checkoutPersistsAnApprovedPayment() {
        CheckoutSessionResponse response = checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);

        PaymentResponse status = checkoutService.findLatest(ORDER_ID, CUSTOMER_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.checkoutUrl()).contains("session_id=sim_" + response.paymentId(), "order_id=10");
        assertThat(status.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(status.provider()).isEqualTo("simulated");
        assertThat(status.checkoutUrl()).isNull();
        assertThat(status.confirmedAt()).isNotNull();
        assertThat(orderApi.confirmCalls).containsExactly(ORDER_ID);
        assertThat(jdbc.queryForObject("select event_id from processed_stripe_event", String.class))
                .isEqualTo("sim_evt_" + response.paymentId());
    }

    @Test
    void orderApiFailureRollsBackTheApprovalAndRetryCompletesIt() {
        orderApi.failure = new OrderApiException("order-api indisponível");

        assertThrows(OrderApiException.class, () -> checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID));
        assertThat(jdbc.queryForObject("select status from payment", String.class)).isEqualTo("PENDING");
        assertThat(count("processed_stripe_event")).isZero();

        orderApi.failure = null;
        CheckoutSessionResponse response = checkoutService.startCheckout(ORDER_ID, CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(count("payment")).isEqualTo(1);
    }

    @Test
    void concurrentCheckoutsApproveOnlyOnePayment() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
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

        assertThat(outcomes).allMatch(o -> o instanceof CheckoutSessionResponse || Integer.valueOf(409).equals(o));
        assertThat(jdbc.queryForObject("select count(*) from payment where status = 'APPROVED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment where status in ('CREATED', 'PENDING')",
                Integer.class)).isZero();
    }

    /** Passa pelo filtro, controller e service: o gateway enxerga a requisição em andamento. */
    @Test
    void checkoutUrlFollowsTheHostOfTheHttpRequest() throws Exception {
        MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new RequestContextFilter())
                .build()
                .perform(post("/ws/payments/orders/{orderId}/checkout-session", ORDER_ID)
                        .header("Host", "192.168.0.18:8080")
                        .header("X-Forwarded-Host", "evil.example")
                        .header("X-User-Id", CUSTOMER_ID)
                        .header("X-User-Type", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl", startsWith(
                        "http://192.168.0.18:8080/payments/return/success?session_id=sim_")));
    }

    @Test
    void webhookIsRejected() {
        var response = webhookController.receive("{}", "t=1,v1=abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
