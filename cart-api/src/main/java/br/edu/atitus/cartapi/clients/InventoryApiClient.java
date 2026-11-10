package br.edu.atitus.cartapi.clients;

import br.edu.atitus.cartapi.dtos.InventoryData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

@Component
public class InventoryApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public InventoryApiClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${cart.clients.inventory-api.base-url:http://inventory-api}") String baseUrl,
            @Value("${cart.security.service-token:}") String serviceToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    public Optional<InventoryData> getAvailability(Long productId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A autenticação serviço-a-serviço não está configurada."
            );
        }
        try {
            InventoryData inventory = restClient.get()
                    .uri(baseUrl + "/ws/inventory/{productId}/availability", productId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(InventoryData.class);
            if (inventory == null || inventory.productId() == null || inventory.productType() == null
                    || inventory.availableQuantity() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "O inventory-api retornou dados incompletos para a disponibilidade."
                );
            }
            return Optional.of(inventory);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "O inventory-api não conseguiu consultar a disponibilidade."
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível consultar o inventory-api.");
        }
    }
}
