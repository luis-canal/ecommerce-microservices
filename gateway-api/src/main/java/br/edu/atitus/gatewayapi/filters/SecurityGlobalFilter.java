package br.edu.atitus.gatewayapi.filters;

import br.edu.atitus.gatewayapi.security.Access;
import br.edu.atitus.gatewayapi.security.AccessPolicy;
import br.edu.atitus.gatewayapi.security.JwtClaims;
import br.edu.atitus.gatewayapi.security.JwtService;
import br.edu.atitus.gatewayapi.security.RevocationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class SecurityGlobalFilter implements GlobalFilter, Ordered {
    private static final List<String> TRUST_HEADERS = List.of(
            "X-User-Id", "X-User-Type", "X-User-Email", "X-Token-Id", "X-Internal-Key");

    private final AccessPolicy policy;
    private final JwtService jwtService;
    private final RevocationClient revocations;
    private final String internalKey;

    public SecurityGlobalFilter(AccessPolicy policy, JwtService jwtService, RevocationClient revocations,
                                @Value("${internal.api-key}") String internalKey) {
        this.policy = policy;
        this.jwtService = jwtService;
        this.revocations = revocations;
        this.internalKey = internalKey;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Access access = policy.resolve(exchange.getRequest().getMethod(), path);
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

        if (access == Access.BLOCKED) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }

        if (access == Access.PUBLIC) {
            return chain.filter(forward(exchange, null, requestId));
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = bearer(authorization);
        JwtClaims claims = jwtService.parse(token).orElse(null);
        if (claims == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        if (access == Access.ADMIN && !"ADMINISTRADOR".equals(claims.type())) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        return revocations.isRevoked(claims.tokenId())
                .flatMap(revoked -> revoked
                        ? reject(exchange, HttpStatus.UNAUTHORIZED)
                        : chain.filter(forward(exchange, claims, requestId)))
                .onErrorResume(error -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE));
    }

    private ServerWebExchange forward(ServerWebExchange exchange, JwtClaims claims, String requestId) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            TRUST_HEADERS.forEach(headers::remove);
            headers.set("X-Internal-Key", internalKey);
            headers.set("X-Request-Id", requestId);
            if (claims != null) {
                headers.set("X-User-Id", claims.userId());
                headers.set("X-User-Type", claims.type());
                if (claims.email() != null) {
                    headers.set("X-User-Email", claims.email());
                }
                headers.set("X-Token-Id", claims.tokenId());
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}