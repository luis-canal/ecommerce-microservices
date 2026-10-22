package br.edu.atitus.gatewayapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RevocationClientTest {
    @Test
    void cachesRevocationLookupByTokenId() {
        AtomicInteger requests = new AtomicInteger();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.incrementAndGet();
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("false")
                    .build());
        });
        RevocationClient client = new RevocationClient(builder, "internal-key");

        assertFalse(client.isRevoked("jti-1").block());
        assertFalse(client.isRevoked("jti-1").block());

        assertEquals(1, requests.get());
    }
}