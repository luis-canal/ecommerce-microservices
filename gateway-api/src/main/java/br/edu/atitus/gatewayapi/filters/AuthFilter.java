package br.edu.atitus.gatewayapi.filters;

import br.edu.atitus.gatewayapi.components.JwtUtil;
import br.edu.atitus.gatewayapi.components.TokenRevocationClient;
import io.jsonwebtoken.Claims;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PROTECTED_ROUTES = List.of("/ws/");
    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-User-Id",
            "X-User-Type",
            "X-User-Email",
            "X-Service-Token",
            "X-Gateway-Token"
    );

    private final TokenRevocationClient tokenRevocationClient;
    private final String serviceToken;
    private final String inventoryDownstreamToken;
    private final String orderDownstreamToken;

    public AuthFilter(
            TokenRevocationClient tokenRevocationClient,
            @Value("${gateway.security.service-token:}") String serviceToken,
            @Value("${gateway.security.inventory-downstream-token:}") String inventoryDownstreamToken,
            @Value("${gateway.security.order-downstream-token:}") String orderDownstreamToken
    ) {
        this.tokenRevocationClient = tokenRevocationClient;
        this.serviceToken = serviceToken;
        this.inventoryDownstreamToken = inventoryDownstreamToken;
        this.orderDownstreamToken = orderDownstreamToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(headers -> TRUSTED_HEADERS.forEach(headers::remove))
                .build();
        String path = request.getURI().getPath();

        if (PROTECTED_ROUTES.stream().noneMatch(path::startsWith)) {
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        String suppliedServiceToken = request.getHeaders().getFirst("X-Service-Token");
        if (isInventoryPath(path) && isValidServiceToken(suppliedServiceToken)) {
            ServerHttpRequest serviceRequest = sanitizedRequest.mutate()
                    .headers(headers -> headers.set("X-Service-Token", suppliedServiceToken))
                    .build();
            return forwardTrustedDownstreamRequest(exchange, chain, serviceRequest, path);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(jwt);
            if (claims != null) {
                Long userId = claims.get("id", Long.class);
                Integer userType = claims.get("type", Integer.class);
                String email = claims.get("email", String.class);
                if (userId != null && userType != null && email != null) {
                    ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                            .headers(headers -> {
                                headers.set("X-User-Id", userId.toString());
                                headers.set("X-User-Type", userType.toString());
                                headers.set("X-User-Email", email);
                            })
                            .build();
                    return tokenRevocationClient.isRevoked(jwt)
                            .materialize()
                            .flatMap(result -> {
                                if (!result.hasValue()) {
                                    return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                                }
                                if (result.get()) {
                                    return reject(exchange, HttpStatus.UNAUTHORIZED);
                                }
                                return forwardTrustedDownstreamRequest(exchange, chain, authenticatedRequest, path);
                            });
                }
            }
        }

        return reject(exchange, HttpStatus.UNAUTHORIZED);
    }

    private boolean isValidServiceToken(String suppliedToken) {
        return serviceToken != null
                && !serviceToken.isBlank()
                && suppliedToken != null
                && MessageDigest.isEqual(
                        serviceToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8)
                );
    }

    private Mono<Void> forwardTrustedDownstreamRequest(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request,
            String path
    ) {
        if (isInventoryPath(path)) {
            return forwardWithDownstreamToken(exchange, chain, request, inventoryDownstreamToken);
        }
        if (isOrderPath(path)) {
            return forwardWithDownstreamToken(exchange, chain, request, orderDownstreamToken);
        }
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> forwardWithDownstreamToken(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request,
            String downstreamToken
    ) {
        if (downstreamToken == null || downstreamToken.isBlank()) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
        }
        ServerHttpRequest signedRequest = request.mutate()
                .headers(headers -> headers.set("X-Gateway-Token", downstreamToken))
                .build();
        return chain.filter(exchange.mutate().request(signedRequest).build());
    }

    private boolean isInventoryPath(String path) {
        return path.equals("/ws/inventory") || path.startsWith("/ws/inventory/");
    }

    private boolean isOrderPath(String path) {
        return path.equals("/ws/orders") || path.startsWith("/ws/orders/")
                || path.equals("/ws/admin/orders") || path.startsWith("/ws/admin/orders/");
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
