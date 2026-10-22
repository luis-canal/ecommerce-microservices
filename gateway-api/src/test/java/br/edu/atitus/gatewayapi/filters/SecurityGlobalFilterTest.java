package br.edu.atitus.gatewayapi.filters;

import br.edu.atitus.gatewayapi.security.Access;
import br.edu.atitus.gatewayapi.security.AccessPolicy;
import br.edu.atitus.gatewayapi.security.JwtClaims;
import br.edu.atitus.gatewayapi.security.JwtService;
import br.edu.atitus.gatewayapi.security.RevocationClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityGlobalFilterTest {
    private final AccessPolicy policy = new AccessPolicy(false);
    private final JwtService jwt = mock(JwtService.class);
    private final RevocationClient revocations = mock(RevocationClient.class);
    private final SecurityGlobalFilter filter = new SecurityGlobalFilter(policy, jwt, revocations, "internal-key");

    @Test
    void rejectsBlockedPathAndRemovesClientTrustHeadersBeforePublicForward() {
        MockServerWebExchange blocked = exchange("GET", "/internal/secrets", null);
        filter.filter(blocked, exchange -> Mono.empty()).block();
        assertEquals(HttpStatus.NOT_FOUND, blocked.getResponse().getStatusCode());

        MockServerWebExchange publicRequest = exchange("GET", "/products/1", null);
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> forwarded = new AtomicReference<>();
        filter.filter(publicRequest, exchange -> {
            forwarded.set(exchange.getRequest());
            return Mono.empty();
        }).block();

        assertEquals("internal-key", forwarded.get().getHeaders().getFirst("X-Internal-Key"));
        UUID.fromString(forwarded.get().getHeaders().getFirst("X-Request-Id"));
        assertNull(forwarded.get().getHeaders().getFirst("X-User-Type"));
        assertNull(forwarded.get().getHeaders().getFirst("X-Token-Id"));
        assertNotNull(publicRequest.getResponse().getHeaders().getFirst("X-Request-Id"));
        verify(jwt, never()).parse(anyString());
    }

    @Test
    void requiresJwtAndAdminRoleAndChecksRevocation() {
        MockServerWebExchange missingToken = exchange("GET", "/ws/orders", null);
        filter.filter(missingToken, exchange -> Mono.empty()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, missingToken.getResponse().getStatusCode());

        when(jwt.parse("client-token")).thenReturn(Optional.of(
                new JwtClaims("42", "CLIENTE", "client@example.test", "jti-client")));
        MockServerWebExchange forbidden = exchange("GET", "/ws/admin/orders", "client-token");
        filter.filter(forbidden, exchange -> Mono.empty()).block();
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getResponse().getStatusCode());
        verify(revocations, never()).isRevoked("jti-client");

        when(jwt.parse("admin-token")).thenReturn(Optional.of(
                new JwtClaims("7", "ADMINISTRADOR", "admin@example.test", "jti-admin")));
        when(revocations.isRevoked("jti-admin")).thenReturn(Mono.just(false));
        MockServerWebExchange admin = exchange("GET", "/ws/admin/orders", "admin-token");
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> forwarded = new AtomicReference<>();
        filter.filter(admin, exchange -> {
            forwarded.set(exchange.getRequest());
            return Mono.empty();
        }).block();
        assertEquals("ADMINISTRADOR", forwarded.get().getHeaders().getFirst("X-User-Type"));
        assertEquals("jti-admin", forwarded.get().getHeaders().getFirst("X-Token-Id"));
    }

    @Test
    void returns503WhenRevocationServiceIsUnavailable() {
        when(jwt.parse("token")).thenReturn(Optional.of(
                new JwtClaims("42", "CLIENTE", "client@example.test", "jti")));
        when(revocations.isRevoked("jti")).thenReturn(Mono.error(new IllegalStateException("auth unavailable")));
        MockServerWebExchange exchange = exchange("GET", "/ws/orders", "token");

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    private static MockServerWebExchange exchange(String method, String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
                .header("X-User-Id", "999")
                .header("X-User-Type", "ADMINISTRADOR")
                .header("X-User-Email", "spoofed@example.test")
                .header("X-Token-Id", "spoofed-jti")
                .header("X-Internal-Key", "spoofed-key");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return MockServerWebExchange.from(builder);
    }
}