package br.edu.atitus.analyticsapi.clients;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
public class InventoryApiClient {

    private static final ParameterizedTypeReference<PageResponse<SourceInventory.Item>> ITEM_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PageResponse<SourceInventory.Movement>> MOVEMENT_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;
    private final RetryExecutor retryExecutor;

    public InventoryApiClient(
            @Qualifier("loadBalancedRestClientBuilder") @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${analytics.clients.inventory-api.base-url:http://inventory-api}") String baseUrl,
            @Value("${analytics.security.service-token:}") String serviceToken,
            RetryExecutor retryExecutor
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
        this.retryExecutor = retryExecutor;
    }

    public SourceInventory.Summary fetchSummary() {
        OrderApiClient.requireServiceToken(serviceToken);
        SourceInventory.Summary summary = retryExecutor.execute("inventory-api /analytics/summary", () ->
                restClient.get()
                        .uri(baseUrl + "/ws/inventory/analytics/summary")
                        .header("X-Service-Token", serviceToken)
                        .retrieve()
                        .body(SourceInventory.Summary.class));
        if (summary == null) {
            throw new IllegalStateException("O inventory-api retornou um resumo vazio.");
        }
        return summary;
    }

    public PageResponse<SourceInventory.Item> fetchOutOfStock(int page, int size) {
        return fetchItems("/ws/inventory/analytics/out-of-stock", page, size);
    }

    public PageResponse<SourceInventory.Item> fetchLowStock(int page, int size) {
        return fetchItems("/ws/inventory/analytics/low-stock", page, size);
    }

    public PageResponse<SourceInventory.Movement> fetchMovements(Instant from, Instant to, int page, int size) {
        OrderApiClient.requireServiceToken(serviceToken);
        PageResponse<SourceInventory.Movement> response = retryExecutor.execute(
                "inventory-api /analytics/movements", () ->
                        restClient.get()
                                .uri(baseUrl + "/ws/inventory/analytics/movements?from={from}&to={to}"
                                                + "&page={page}&size={size}&sort=createdAt,asc&sort=id,asc",
                                        from.toString(), to.toString(), page, size)
                                .header("X-Service-Token", serviceToken)
                                .retrieve()
                                .body(MOVEMENT_PAGE));
        if (response == null) {
            throw new IllegalStateException("O inventory-api retornou uma página de movimentações vazia.");
        }
        return response;
    }

    private PageResponse<SourceInventory.Item> fetchItems(String path, int page, int size) {
        OrderApiClient.requireServiceToken(serviceToken);
        PageResponse<SourceInventory.Item> response = retryExecutor.execute("inventory-api " + path, () ->
                restClient.get()
                        .uri(baseUrl + path + "?page={page}&size={size}&sort=productId,asc", page, size)
                        .header("X-Service-Token", serviceToken)
                        .retrieve()
                        .body(ITEM_PAGE));
        if (response == null) {
            throw new IllegalStateException("O inventory-api retornou uma página vazia em " + path + ".");
        }
        return response;
    }
}
