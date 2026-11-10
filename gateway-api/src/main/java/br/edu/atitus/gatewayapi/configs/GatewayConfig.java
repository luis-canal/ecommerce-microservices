package br.edu.atitus.gatewayapi.configs;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator getRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Rotas públicas
                .route("product-api", route -> route
                        .path("/products/**")
                        .uri("lb://product-api"))
                .route("currency-api", route -> route
                        .path("/currencies/**")
                        .uri("lb://currency-api"))
                .route("auth-api", route -> route
                        .path("/auth/**")
                        .uri("lb://auth-api"))
                // Rotas Protegidas (/ws/**)
                .route("product-api-ws", route -> route
                        .path("/ws/products/**")
                        .uri("lb://product-api"))
                .route("currency-api-ws", route -> route
                        .path("/ws/currencies/**")
                        .uri("lb://currency-api"))
                .route("auth-api-ws", route -> route
                        .path("/ws/auth/**")
                        .uri("lb://auth-api"))
                .route("inventory-api-ws", route -> route
                        .path("/ws/inventory/**")
                        .uri("lb://inventory-api"))
                .route("order-api-admin-ws", route -> route
                        .path("/ws/admin/orders/**")
                        .uri("lb://order-api"))
                .route("order-api-ws", route -> route
                        .path("/ws/orders/**")
                        .uri("lb://order-api"))
                .route("cart-api-ws", route -> route
                        .path("/ws/cart", "/ws/cart/**")
                        .uri("lb://cart-api"))
                .build();
    }
}
