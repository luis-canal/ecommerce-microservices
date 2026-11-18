package br.edu.atitus.paymentapi.controllers;

import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.config.GatewayTokenFilter;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.services.CheckoutService;
import br.edu.atitus.paymentapi.services.WebhookService;
import br.edu.atitus.paymentapi.support.FakePaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ControllersTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final WebhookService webhookService = mock(WebhookService.class);
    private final WebhookController webhookController = new WebhookController(gateway, webhookService);

    @Test
    void invalidSignatureIsBadRequest() {
        var response = webhookController.receive("{}", "invalid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(webhookService);
    }

    @Test
    void processedEventIsOk() {
        gateway.nextEvent = event();

        var response = webhookController.receive("{}", "valid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookService).process(gateway.nextEvent);
    }

    @Test
    void processingFailureIsServerErrorSoStripeRetries() {
        gateway.nextEvent = event();
        doThrow(new OrderApiException("order-api respondeu 404")).when(webhookService).process(any());

        var response = webhookController.receive("{}", "valid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void onlyCustomersCanPay() {
        CheckoutService checkoutService = mock(CheckoutService.class);
        PaymentController controller = new PaymentController(checkoutService);

        ResponseStatusException admin = assertThrows(ResponseStatusException.class,
                () -> controller.createCheckoutSession(1L, 1L, 0));
        ResponseStatusException status = assertThrows(ResponseStatusException.class,
                () -> controller.findPayment(1L, 1L, null));

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(checkoutService);
    }

    @Test
    void gatewayTokenRequiredOnPaymentRoutesButNotOnWebhook() throws Exception {
        GatewayTokenFilter filter = new GatewayTokenFilter("gateway-token");

        MockHttpServletResponse withoutToken = new MockHttpServletResponse();
        MockFilterChain blocked = new MockFilterChain();
        filter.doFilter(request("/ws/payments/orders/1/checkout-session", null), withoutToken, blocked);
        assertThat(withoutToken.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(blocked.getRequest()).isNull();

        MockFilterChain allowed = new MockFilterChain();
        filter.doFilter(request("/ws/payments/orders/1", "gateway-token"), new MockHttpServletResponse(), allowed);
        assertThat(allowed.getRequest()).isNotNull();

        MockFilterChain webhook = new MockFilterChain();
        filter.doFilter(request("/payments/webhook", null), new MockHttpServletResponse(), webhook);
        assertThat(webhook.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest request(String uri, String gatewayToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (gatewayToken != null) {
            request.addHeader("X-Gateway-Token", gatewayToken);
        }
        return request;
    }

    private static PaymentEvent event() {
        return new PaymentEvent("evt_1", "checkout.session.completed", PaymentEventType.CHECKOUT_COMPLETED,
                "cs_test_1", 1L, "pi_1", true);
    }
}
