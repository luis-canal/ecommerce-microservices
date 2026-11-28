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
                .route("category-api", route -> route
                        .path("/categories/**")
                        .uri("lb://product-api"))
                .route("currency-api", route -> route
                        .path("/currencies/**")
                        .uri("lb://currency-api"))
                .route("auth-api", route -> route
                        .path("/auth/**")
                        .uri("lb://auth-api"))
                // Webhook do Stripe: público, autenticado pela assinatura no payment-api.
                .route("payment-api-webhook", route -> route
                        .path("/payments/webhook")
                        .uri("lb://payment-api"))
                // Retorno do navegador após o Stripe: público, só redireciona para o deep link do app.
                .route("payment-api-return", route -> route
                        .path("/payments/return/success", "/payments/return/cancel")
                        .uri("lb://payment-api"))
                // Rotas Protegidas (/ws/**)
                .route("product-api-ws", route -> route
                        .path("/ws/products/**")
                        .uri("lb://product-api"))
                .route("category-api-ws", route -> route
                        .path("/ws/categories/**")
                        .uri("lb://product-api"))
                .route("subcategory-api-ws", route -> route
                        .path("/ws/subcategories/**")
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
                .route("analytics-api-admin-ws", route -> route
                        .path("/ws/admin/analytics", "/ws/admin/analytics/**")
                        .uri("lb://analytics-api"))
                .route("order-api-admin-ws", route -> route
                        .path("/ws/admin/orders/**")
                        .uri("lb://order-api"))
                .route("order-api-ws", route -> route
                        .path("/ws/orders/**")
                        .uri("lb://order-api"))
                .route("cart-api-ws", route -> route
                        .path("/ws/cart", "/ws/cart/**")
                        .uri("lb://cart-api"))
                // Preserva o Host usado pelo cliente: o payment-api monta com ele o checkoutUrl do
                // modo simulado. Os cabeçalhos X-Forwarded-* continuam não sendo encaminhados.
                .route("payment-api-ws", route -> route
                        .path("/ws/payments", "/ws/payments/**")
                        .filters(filters -> filters.preserveHostHeader())
                        .uri("lb://payment-api"))
                .build();
    }
}
