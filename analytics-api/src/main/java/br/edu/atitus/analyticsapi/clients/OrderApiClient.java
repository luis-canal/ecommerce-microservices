package br.edu.atitus.analyticsapi.clients;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
public class OrderApiClient {

    private static final ParameterizedTypeReference<PageResponse<SourceOrder>> ORDER_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;
    private final RetryExecutor retryExecutor;

    public OrderApiClient(
            @Qualifier("loadBalancedRestClientBuilder") @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${analytics.clients.order-api.base-url:http://order-api}") String baseUrl,
            @Value("${analytics.security.service-token:}") String serviceToken,
            RetryExecutor retryExecutor
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
        this.retryExecutor = retryExecutor;
    }

    public PageResponse<SourceOrder> fetchOrdersUpdatedAfter(Instant updatedAfter, int page, int size) {
        requireServiceToken(serviceToken);
        PageResponse<SourceOrder> response = retryExecutor.execute("order-api /ws/orders/internal/analytics", () ->
                restClient.get()
                        .uri(baseUrl + "/ws/orders/internal/analytics?updatedAfter={updatedAfter}"
                                        + "&page={page}&size={size}&sort=updatedAt,asc&sort=id,asc",
                                updatedAfter.toString(), page, size)
                        .header("X-Service-Token", serviceToken)
                        .retrieve()
                        .body(ORDER_PAGE));
        if (response == null) {
            throw new IllegalStateException("O order-api retornou uma resposta vazia.");
        }
        return response;
    }

    static void requireServiceToken(String serviceToken) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "A autenticação serviço-a-serviço não está configurada (SERVICE_AUTH_TOKEN).");
        }
    }
}
