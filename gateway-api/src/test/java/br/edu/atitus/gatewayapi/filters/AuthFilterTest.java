package br.edu.atitus.gatewayapi.filters;

import br.edu.atitus.gatewayapi.components.TokenRevocationClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthFilterTest {

    @Test
    void rejectsMissingTokenWithPlainTextUnauthorizedResponse() {
        AuthFilter filter = new AuthFilter(mock(TokenRevocationClient.class),
                "service-token", "inventory-token", "order-token",
                "cart-token", "analytics-token", "payment-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/cart").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(MediaType.TEXT_PLAIN.isCompatibleWith(
                exchange.getResponse().getHeaders().getContentType()));
        assertEquals("Token ausente, inválido ou revogado.",
                exchange.getResponse().getBodyAsString().block());
    }

    @Test
    void mapsGatewayNotFoundToSafePlainTextMessage() {
        GatewayErrorHandler handler = new GatewayErrorHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/unknown").build()
        );

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "internal detail")).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("Recurso não encontrado.", exchange.getResponse().getBodyAsString().block());
    }

    @Test
    void forwardsServiceErrorStatusAndBodyUnchanged() {
        AuthFilter filter = new AuthFilter(mock(TokenRevocationClient.class),
                "service-token", "inventory-token", "order-token",
                "cart-token", "analytics-token", "payment-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/products/42").build()
        );

        filter.filter(exchange, downstream -> {
            downstream.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            downstream.getResponse().getHeaders().setContentType(
                    MediaType.parseMediaType("text/plain;charset=UTF-8"));
            byte[] body = "Produto não encontrado.".getBytes(StandardCharsets.UTF_8);
            return downstream.getResponse().writeWith(
                    Mono.just(downstream.getResponse().bufferFactory().wrap(body)));
        }).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("Produto não encontrado.", exchange.getResponse().getBodyAsString().block());
    }
}
