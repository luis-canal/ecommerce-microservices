package br.edu.atitus.gatewayapi.configs;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GatewayConfig {
    private static final long RESPONSE_TIMEOUT_MILLIS = 2_000;

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-api", route -> route.path("/auth/**", "/ws/auth/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://auth-api"))
                .route("product-api-public", route -> route.path("/products/**", "/categories/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://product-api"))
                .route("product-api-admin", route -> route.path("/ws/admin/products/**",
                                "/ws/admin/categories/**", "/ws/admin/attributes/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://product-api"))
                .route("currency-api", route -> route.path("/currencies/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://currency-api"))
                .route("inventory-api", route -> route.path("/ws/admin/inventory/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://inventory-api"))
                .route("order-api", route -> route.path("/ws/cart/**", "/ws/checkout/**", "/ws/orders/**",
                                "/ws/admin/orders/**", "/ws/shipping/**", "/ws/admin/shipping/**",
                                "/ws/users/me/addresses/**", "/addresses/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://order-api"))
                .route("analytics-api", route -> route.path("/ws/admin/analytics/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://analytics-api"))
                // Mantido temporariamente para os controllers atuais do product-api até P2.
                .route("product-api-legacy-ws", route -> route.path("/ws/products/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://product-api"))
                .route("product-api-legacy-currency-ws", route -> route.path("/ws/currencies/**")
                        .metadata("response-timeout", RESPONSE_TIMEOUT_MILLIS)
                        .uri("lb://currency-api"))
                .build();
    }
}
