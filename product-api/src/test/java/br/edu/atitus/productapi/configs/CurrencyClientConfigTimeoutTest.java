package br.edu.atitus.productapi.configs;

import br.edu.atitus.productapi.clients.CurrencyClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyClientConfigTimeoutTest {

    @Test
    void abortsReadWhenCurrencyApiResponseExceedsConfiguredTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/currencies", exchange -> {
            try {
                Thread.sleep(4_000);
                byte[] body = "{\"sourceCurrency\":\"USD\",\"targetCurrency\":\"BRL\",\"conversionRate\":5.0,\"environment\":\"test\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client is expected to close the connection after its read timeout.
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            CurrencyClient client = new CurrencyClientConfig().getClient(
                    RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );
            long startedAt = System.nanoTime();

            assertThrows(RestClientException.class, () -> client.getCurrency("USD", "BRL"));
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            assertTrue(elapsedMillis < 3_500,
                    "Expected the configured 2-second read timeout, elapsed: " + elapsedMillis + "ms");
        } finally {
            server.stop(0);
        }
    }
}
