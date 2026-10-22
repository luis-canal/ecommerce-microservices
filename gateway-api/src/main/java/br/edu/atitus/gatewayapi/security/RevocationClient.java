package br.edu.atitus.gatewayapi.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RevocationClient {
    private final WebClient webClient;
    private final String internalKey;
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(10_000)
            .build();

    public RevocationClient(WebClient.Builder webClientBuilder,
                            @Value("${internal.api-key}") String internalKey) {
        this.webClient = webClientBuilder.build();
        this.internalKey = internalKey;
    }

    public Mono<Boolean> isRevoked(String tokenId) {
        Boolean cached = cache.getIfPresent(tokenId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.get()
                .uri("http://auth-api/internal/tokens/{jti}/revoked", tokenId)
                .header("X-Internal-Key", internalKey)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofSeconds(2))
                .doOnNext(value -> cache.put(tokenId, value));
    }
}