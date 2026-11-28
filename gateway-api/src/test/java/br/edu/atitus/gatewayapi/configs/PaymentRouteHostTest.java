package br.edu.atitus.gatewayapi.configs;

import br.edu.atitus.gatewayapi.components.TokenRevocationClient;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Gateway real encaminhando para um serviço local que registra os cabeçalhos recebidos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // O application.properties exige "consul:"; sem servidor na porta 1, nada é carregado.
        "spring.cloud.consul.port=1",
        "spring.cloud.consul.config.fail-fast=false",
        "spring.config.on-not-found=ignore",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "app.auth.internal-url=http://localhost:1",
        "app.auth.internal-secret=test-secret",
        "gateway.security.payment-downstream-token=payment-token"
})
class PaymentRouteHostTest {

    private static final Map<String, String> RECEIVED = new ConcurrentHashMap<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) {
        String uri = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.payment-api[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.product-api[0].uri", () -> uri);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private TokenRevocationClient tokenRevocationClient;

    @BeforeEach
    void reset() {
        RECEIVED.clear();
        when(tokenRevocationClient.isRevoked(anyString())).thenReturn(Mono.just(false));
    }

    @Test
    void paymentRouteKeepsTheClientHostAndDropsForwardedHeaders() {
        client().post().uri("/ws/payments/orders/1/checkout-session")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header(HttpHeaders.HOST, "192.168.0.18:8080")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .header("Forwarded", "host=evil.example")
                .exchange()
                .expectStatus().isOk();

        assertEquals("192.168.0.18:8080", RECEIVED.get("host"));
        assertNull(RECEIVED.get("x-forwarded-host"));
        assertNull(RECEIVED.get("x-forwarded-proto"));
        assertNull(RECEIVED.get("forwarded"));
    }

    @Test
    void otherRoutesStillReceiveTheServiceHost() {
        client().get().uri("/products/1")
                .header(HttpHeaders.HOST, "192.168.0.18:8080")
                .exchange()
                .expectStatus().isOk();

        assertEquals("127.0.0.1:" + DOWNSTREAM.getAddress().getPort(), RECEIVED.get("host"));
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static String customerToken() {
        // Mesma chave de exemplo do JwtUtil.
        return Jwts.builder()
                .claim("id", 3L)
                .claim("type", 1)
                .claim("email", "cliente@example.com")
                .signWith(Keys.hmacShaKeyFor(
                        "chaveSuperSecretaParaJWTdeExemplo!@#123".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestHeaders().forEach((name, values) ->
                        RECEIVED.put(name.toLowerCase(), values.getFirst()));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
