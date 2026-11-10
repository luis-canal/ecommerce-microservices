package br.edu.atitus.cartapi.clients;

import br.edu.atitus.cartapi.dtos.ProductData;
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
public class ProductApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public ProductApiClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${cart.clients.product-api.base-url:http://product-api}") String baseUrl,
            @Value("${cart.security.service-token:}") String serviceToken
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    public Optional<ProductData> getProduct(Long productId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A autenticação serviço-a-serviço não está configurada."
            );
        }
        try {
            ProductData product = restClient.get()
                    .uri(baseUrl + "/internal/products/{id}/cart-data", productId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(ProductData.class);
            if (product == null || product.id() == null || product.convertedPrice() == null
                    || product.productType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O product-api retornou dados incompletos.");
            }
            return Optional.of(product);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O product-api não conseguiu consultar o produto.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível consultar o product-api.");
        }
    }
}
