package br.edu.atitus.inventoryapi.clients;

import br.edu.atitus.inventoryapi.enums.ProductType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public ProductApiClient(
            @Qualifier("loadBalancedRestClientBuilder") @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${inventory.clients.product-api.base-url:http://product-api}") String baseUrl,
            @Value("${inventory.security.service-token:}") String serviceToken
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    public ProductType getProductType(Long productId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A autenticação serviço-a-serviço não está configurada."
            );
        }

        try {
            InventoryProductData product = restClient.get()
                    .uri(baseUrl + "/internal/products/{productId}/inventory-data", productId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(InventoryProductData.class);
            if (product == null || product.productType() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "O product-api retornou dados incompletos para o produto."
                );
            }
            return product.productType();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado no product-api.");
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "O product-api rejeitou ou não conseguiu processar a consulta do produto."
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível consultar o product-api."
            );
        }
    }

    private record InventoryProductData(Long productId, ProductType productType, boolean active) {
    }
}
