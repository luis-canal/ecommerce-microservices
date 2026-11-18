package br.edu.atitus.orderapi.controllers;

import br.edu.atitus.orderapi.configs.GatewayTokenFilter;
import br.edu.atitus.orderapi.configs.ServiceTokenFilter;
import br.edu.atitus.orderapi.dtos.PaymentInfoResponse;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.services.OrderService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentInfoEndpointTest {

    private static final String SERVICE_TOKEN = "service-token";
    private static final String GATEWAY_TOKEN = "gateway-token";

    private final GatewayTokenFilter gatewayFilter = new GatewayTokenFilter(GATEWAY_TOKEN, SERVICE_TOKEN);
    private final ServiceTokenFilter serviceFilter = new ServiceTokenFilter(SERVICE_TOKEN);

    @Test
    void returnsOnlyThePaymentFields() {
        OrderService orderService = mock(OrderService.class);
        OrderEntity order = new OrderEntity();
        order.setId(7L);
        order.setCustomerId(3L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("149.90"));
        order.setCurrency("BRL");
        Instant expiresAt = Instant.parse("2026-11-18T12:10:00Z");
        order.setReservationExpiresAt(expiresAt);
        when(orderService.findOrderForAdmin(7L)).thenReturn(order);

        PaymentInfoResponse body = new OrderController(orderService).findPaymentInfo(7L).getBody();

        assertEquals(new PaymentInfoResponse(7L, 3L, OrderStatus.PENDING_PAYMENT,
                new BigDecimal("149.90"), "BRL", expiresAt), body);
    }

    @Test
    void acceptsServiceTokenWithoutGatewayToken() throws Exception {
        MockHttpServletRequest request = request("GET", "/ws/orders/internal/7/payment-info");
        request.addHeader("X-Service-Token", SERVICE_TOKEN);

        MockFilterChain chain = runBothFilters(request, new MockHttpServletResponse());

        assertNotNull(chain.getRequest());
    }

    @Test
    void rejectsMissingOrInvalidServiceToken() throws Exception {
        MockHttpServletRequest invalid = request("GET", "/ws/orders/internal/7/payment-info");
        invalid.addHeader("X-Service-Token", "wrong");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();

        MockFilterChain chain = runBothFilters(invalid, invalidResponse);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), invalidResponse.getStatus());
    }

    /**
     * Pelo Gateway, o cliente chega com X-Gateway-Token válido, mas o Gateway remove qualquer
     * X-Service-Token enviado por ele. Os endpoints internos precisam responder 401 nesse caso.
     */
    @Test
    void customerThroughGatewayGetsUnauthorizedOnInternalEndpoints() throws Exception {
        for (String[] endpoint : new String[][]{
                {"GET", "/ws/orders/internal/7/payment-info"},
                {"POST", "/ws/orders/7/confirm"},
                {"POST", "/ws/orders/7/cancel"}
        }) {
            MockHttpServletRequest request = request(endpoint[0], endpoint[1]);
            request.addHeader("X-Gateway-Token", GATEWAY_TOKEN);
            request.addHeader("X-User-Id", "3");
            request.addHeader("X-User-Type", "1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            MockFilterChain chain = runBothFilters(request, response);

            assertNull(chain.getRequest(), endpoint[1]);
            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus(), endpoint[1]);
        }
    }

    private MockFilterChain runBothFilters(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        MockFilterChain finalChain = new MockFilterChain();
        FilterChain throughServiceFilter = (req, res) -> serviceFilter.doFilter(req, res, finalChain);
        gatewayFilter.doFilter(request, response, throughServiceFilter);
        return finalChain;
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
