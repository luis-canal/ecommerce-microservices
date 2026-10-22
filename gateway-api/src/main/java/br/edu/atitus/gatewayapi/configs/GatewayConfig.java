package br.edu.atitus.gatewayapi.configs;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GatewayConfig {
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-api", route -> route.path("/auth/**", "/ws/auth/**")
                        .uri("lb://auth-api"))
                .route("product-api-public", route -> route.path("/products/**", "/categories/**")
                        .uri("lb://product-api"))
                .route("product-api-admin", route -> route.path("/ws/admin/products/**",
                                "/ws/admin/categories/**", "/ws/admin/attributes/**")
                        .uri("lb://product-api"))
                .route("currency-api", route -> route.path("/currencies/**")
                        .uri("lb://currency-api"))
                .route("inventory-api", route -> route.path("/ws/admin/inventory/**")
                        .uri("lb://inventory-api"))
                .route("order-api", route -> route.path("/ws/cart/**", "/ws/checkout/**", "/ws/orders/**",
                                "/ws/admin/orders/**", "/ws/shipping/**", "/ws/admin/shipping/**",
                                "/ws/users/me/addresses/**", "/addresses/**")
                        .uri("lb://order-api"))
                .route("analytics-api", route -> route.path("/ws/admin/analytics/**")
                        .uri("lb://analytics-api"))
                // Mantido temporariamente para os controllers atuais do product-api até P2.
                .route("product-api-legacy-ws", route -> route.path("/ws/products/**")
                        .uri("lb://product-api"))
                .route("product-api-legacy-currency-ws", route -> route.path("/ws/currencies/**")
                        .uri("lb://currency-api"))
                .build();
    }
}
