package br.edu.atitus.paymentapi.provider.simulated;

import br.edu.atitus.paymentapi.provider.CheckoutSession;
import br.edu.atitus.paymentapi.provider.CheckoutSessionRequest;
import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulatedPaymentGatewayTest {

    private static final CheckoutSessionRequest REQUEST =
            new CheckoutSessionRequest(15L, 42L, 3L, new BigDecimal("149.90"), "BRL");

    @Test
    void withoutSuccessUrlUsesTheReturnBaseUrl() {
        CheckoutSession session = new SimulatedPaymentGateway("", "http://localhost:8080/")
                .createCheckoutSession(REQUEST);

        assertThat(session.sessionId()).isEqualTo("sim_15");
        assertThat(session.url())
                .isEqualTo("http://localhost:8080/payments/return/success?session_id=sim_15&order_id=42");
    }

    @Test
    void withoutAnyUrlStillStarts() {
        CheckoutSession session = new SimulatedPaymentGateway(null, " ").createCheckoutSession(REQUEST);

        assertThat(session.url())
                .isEqualTo("http://localhost:8080/payments/return/success?session_id=sim_15&order_id=42");
    }

    @Test
    void configuredSuccessUrlWinsAndReceivesSessionAndOrder() {
        CheckoutSession session = new SimulatedPaymentGateway(
                "http://192.168.0.18:8080/payments/return/success?session_id={CHECKOUT_SESSION_ID}",
                "http://localhost:8080").createCheckoutSession(REQUEST);

        assertThat(session.url())
                .isEqualTo("http://192.168.0.18:8080/payments/return/success?session_id=sim_15&order_id=42");
    }

    @Test
    void successUrlWithoutPlaceholderGetsTheSessionIdBeforeTheFragment() {
        CheckoutSession session = new SimulatedPaymentGateway(
                "http://loja.test/payments/return/success#fim", "").createCheckoutSession(REQUEST);

        assertThat(session.url())
                .isEqualTo("http://loja.test/payments/return/success?session_id=sim_15&order_id=42#fim");
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.18:8080", "10.0.2.2:8080", "localhost:18080", "loja.example.com", "[::1]:8080"})
    void withoutSuccessUrlUsesTheHostOfTheRequest(String host) {
        request("http", host);

        assertThat(gateway().createCheckoutSession(REQUEST).url())
                .isEqualTo("http://" + host + "/payments/return/success?session_id=sim_15&order_id=42");
    }

    @Test
    void keepsTheSchemeOfTheRequest() {
        request("https", "loja.example.com");

        assertThat(gateway().createCheckoutSession(REQUEST).url()).startsWith("https://loja.example.com/");
    }

    @Test
    void configuredSuccessUrlWinsOverTheRequestHost() {
        request("http", "192.168.0.18:8080");

        CheckoutSession session = new SimulatedPaymentGateway(
                "http://configurada.test/ok?session_id={CHECKOUT_SESSION_ID}", "").createCheckoutSession(REQUEST);

        assertThat(session.url()).isEqualTo("http://configurada.test/ok?session_id=sim_15&order_id=42");
    }

    @Test
    void forwardedHeadersAreIgnored() {
        MockHttpServletRequest request = request("http", "192.168.0.18:8080");
        request.addHeader("X-Forwarded-Host", "evil.example");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Port", "444");
        request.addHeader("Forwarded", "host=evil.example;proto=https");

        assertThat(gateway().createCheckoutSession(REQUEST).url()).startsWith("http://192.168.0.18:8080/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "evil.example/path", "user@evil.example", "evil.example?x=1", "evil.example#x",
            "evil.example\\@loja", "evil .example", "evil.example,outro.example", "evil.example\r\nX: 1",
            "loja.example:0", "loja.example:65536", "loja.example:abc", "loja.example:", ".loja.example",
            "http://evil.example", "[::1", "%65vil.example"
    })
    void invalidHostFallsBackToTheReturnBaseUrl(String host) {
        request("http", host);

        assertThat(gateway().createCheckoutSession(REQUEST).url())
                .isEqualTo("http://localhost:8080/payments/return/success?session_id=sim_15&order_id=42");
    }

    @Test
    void missingOrOversizedHostOrUnknownSchemeFallsBack() {
        request("http", null);
        assertThat(gateway().createCheckoutSession(REQUEST).url()).startsWith("http://localhost:8080/");

        request("http", "a.".repeat(130) + "com");
        assertThat(gateway().createCheckoutSession(REQUEST).url()).startsWith("http://localhost:8080/");

        request("ftp", "loja.example.com");
        assertThat(gateway().createCheckoutSession(REQUEST).url()).startsWith("http://localhost:8080/");
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest request(String scheme, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        if (host != null) {
            request.addHeader("Host", host);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void immediateResultIsAPaidCheckoutWithSimulatedIds() {
        PaymentEvent event = gateway().immediateResult("sim_15", 15L).orElseThrow();

        assertThat(event.eventId()).isEqualTo("sim_evt_15");
        assertThat(event.type()).isEqualTo(PaymentEventType.CHECKOUT_COMPLETED);
        assertThat(event.sessionId()).isEqualTo("sim_15");
        assertThat(event.paymentId()).isEqualTo(15L);
        assertThat(event.paymentIntentId()).isEqualTo("sim_pi_15");
        assertThat(event.paid()).isTrue();
    }

    @Test
    void sessionOfAnotherProviderIsNeverApproved() {
        assertThat(gateway().immediateResult("cs_test_15", 15L)).isEmpty();
        assertThat(gateway().immediateResult(null, 15L)).isEmpty();
    }

    @Test
    void refundAndExpireDoNothingExternal() {
        assertDoesNotThrow(() -> gateway().refund("sim_pi_15", "refund-15"));
        assertThat(gateway().expireSession("sim_15")).isTrue();
    }

    @Test
    void webhookIsAlwaysRejected() {
        assertThrows(InvalidWebhookSignatureException.class,
                () -> gateway().parseWebhookEvent("{}", "t=1,v1=abc"));
    }

    private static SimulatedPaymentGateway gateway() {
        return new SimulatedPaymentGateway("", "");
    }
}
