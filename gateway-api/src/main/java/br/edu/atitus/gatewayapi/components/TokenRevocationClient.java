package br.edu.atitus.gatewayapi.components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class TokenRevocationClient {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String CHECK_PATH = "/internal/auth/token-revocation/check";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String internalSecret;

    public TokenRevocationClient(
            @Value("${app.auth.internal-url}") String internalUrl,
            @Value("${app.auth.internal-secret}") String internalSecret) {
        this.webClient = WebClient.builder().baseUrl(internalUrl).build();
        this.internalSecret = internalSecret;
    }

    public Mono<Boolean> isRevoked(String token) {
        return webClient.post()
                .uri(CHECK_PATH)
                .header(INTERNAL_AUTH_HEADER, internalSecret)
                .bodyValue(new TokenHashRequest(sha256(token)))
                .retrieve()
                .bodyToMono(Boolean.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Auth API returned an empty revocation response")))
                .timeout(REQUEST_TIMEOUT);
    }

    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record TokenHashRequest(String tokenHash) {
    }
}
