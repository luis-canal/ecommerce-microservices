package br.edu.atitus.paymentapi.provider.stripe;

import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.provider.PaymentProviderException;
import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Não acessa a rede: a validação de assinatura do SDK é local. */
class StripePaymentGatewayTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String SUCCESS_URL = "http://localhost:5173/pagamento/sucesso?session_id={CHECKOUT_SESSION_ID}";
    private static final String CANCEL_URL = "http://localhost:5173/pagamento/cancelado";

    @Test
    void nonTestKeyPreventsStartup() {
        for (String key : new String[]{"sk_live_123", "rk_live_123", "pk_test_123", "", null}) {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gateway(key));
            if (key != null && !key.isEmpty()) {
                assertThat(exception.getMessage()).doesNotContain(key);
            }
        }
    }

    @Test
    void acceptsTestKeys() {
        assertDoesNotThrow(() -> gateway("sk_test_123"));
        assertDoesNotThrow(() -> gateway("rk_test_123"));
    }

    @Test
    void requiresWebhookSecretAndSessionPlaceholderInSuccessUrl() {
        assertThrows(IllegalStateException.class,
                () -> new StripePaymentGateway("sk_test_1", "", SUCCESS_URL, CANCEL_URL, 1000, 1000));
        assertThrows(IllegalStateException.class,
                () -> new StripePaymentGateway("sk_test_1", WEBHOOK_SECRET, "http://localhost/ok", CANCEL_URL, 1000, 1000));
        assertThrows(IllegalStateException.class,
                () -> new StripePaymentGateway("sk_test_1", WEBHOOK_SECRET, SUCCESS_URL, " ", 1000, 1000));
    }

    @Test
    void parsesSignedCheckoutEvent() throws Exception {
        String payload = """
                {"id":"evt_123","object":"event","type":"checkout.session.completed",
                 "api_version":"2020-08-27",
                 "data":{"object":{"id":"cs_test_9","object":"checkout.session","status":"complete",
                 "payment_status":"paid","payment_intent":"pi_test_9",
                 "metadata":{"payment_id":"5","order_id":"10","customer_id":"3"}}}}
                """;

        PaymentEvent event = gateway("sk_test_1").parseWebhookEvent(payload, sign(payload, WEBHOOK_SECRET));

        assertThat(event.eventId()).isEqualTo("evt_123");
        assertThat(event.type()).isEqualTo(PaymentEventType.CHECKOUT_COMPLETED);
        assertThat(event.sessionId()).isEqualTo("cs_test_9");
        assertThat(event.paymentId()).isEqualTo(5L);
        assertThat(event.paymentIntentId()).isEqualTo("pi_test_9");
        assertThat(event.paid()).isTrue();
    }

    @Test
    void unrelatedEventIsReturnedAsIgnored() throws Exception {
        String payload = """
                {"id":"evt_456","object":"event","type":"charge.updated","data":{"object":{"id":"ch_1","object":"charge"}}}
                """;

        PaymentEvent event = gateway("sk_test_1").parseWebhookEvent(payload, sign(payload, WEBHOOK_SECRET));

        assertThat(event.type()).isEqualTo(PaymentEventType.IGNORED);
        assertThat(event.rawType()).isEqualTo("charge.updated");
    }

    @Test
    void rejectsInvalidOrMissingSignature() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{}}}";
        StripePaymentGateway gateway = gateway("sk_test_1");

        assertThrows(InvalidWebhookSignatureException.class,
                () -> gateway.parseWebhookEvent(payload, sign(payload, "whsec_other")));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> gateway.parseWebhookEvent(payload + " ", sign(payload, WEBHOOK_SECRET)));
        assertThrows(InvalidWebhookSignatureException.class, () -> gateway.parseWebhookEvent(payload, null));
    }

    @Test
    void convertsAmountToCentsWithoutRounding() {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("149.90"))).isEqualTo(14990L);
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("1"))).isEqualTo(100L);
        assertThrows(IllegalArgumentException.class, () -> StripePaymentGateway.toMinorUnits(new BigDecimal("1.005")));
        assertThrows(IllegalArgumentException.class, () -> StripePaymentGateway.toMinorUnits(BigDecimal.ZERO));
    }

    @Test
    void refundOfAlreadyRefundedChargeIsTreatedAsSuccess() throws Exception {
        String error = "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"charge_already_refunded\","
                + "\"message\":\"Charge has already been refunded.\"}}";
        withStripeStub(400, error, gateway ->
                assertDoesNotThrow(() -> gateway.refund("pi_test_1", "refund-1")));
    }

    @Test
    void otherRefundErrorsFail() throws Exception {
        String error = "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\","
                + "\"message\":\"No such payment_intent.\"}}";
        withStripeStub(400, error, gateway ->
                assertThrows(PaymentProviderException.class, () -> gateway.refund("pi_test_1", "refund-1")));
    }

    /** Servidor HTTP local no lugar da API do Stripe: nenhuma chamada sai da máquina. */
    private static void withStripeStub(int status, String body, java.util.function.Consumer<StripePaymentGateway> test)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            StripeClient client = StripeClient.builder()
                    .setApiKey("sk_test_stub")
                    .setApiBase("http://127.0.0.1:" + server.getAddress().getPort())
                    .setMaxNetworkRetries(0)
                    .build();
            test.accept(new StripePaymentGateway(client, WEBHOOK_SECRET, SUCCESS_URL, CANCEL_URL));
        } finally {
            server.stop(0);
        }
    }

    private static StripePaymentGateway gateway(String key) {
        return new StripePaymentGateway(key, WEBHOOK_SECRET, SUCCESS_URL, CANCEL_URL, 1000, 1000);
    }

    /** Mesmo esquema do Stripe: t=timestamp,v1=HMAC-SHA256(secret, "timestamp.payload"). */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
