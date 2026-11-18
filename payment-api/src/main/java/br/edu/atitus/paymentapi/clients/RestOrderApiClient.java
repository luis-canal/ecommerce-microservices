package br.edu.atitus.paymentapi.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

@Component
public class RestOrderApiClient implements OrderApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestOrderApiClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public RestOrderApiClient(
            @Qualifier("loadBalancedRestClientBuilder") @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${payment.clients.order-api.base-url:http://order-api}") String baseUrl,
            @Value("${payment.security.service-token:}") String serviceToken
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    @Override
    public Optional<OrderPaymentInfo> getPaymentInfo(Long orderId) {
        requireServiceToken();
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(baseUrl + "/ws/orders/internal/{orderId}/payment-info", orderId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(OrderPaymentInfo.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw new OrderApiException("order-api respondeu " + exception.getStatusCode().value()
                    + " ao consultar o pedido " + orderId + ".", exception);
        } catch (RestClientException exception) {
            throw new OrderApiException("order-api indisponível ao consultar o pedido " + orderId + ".", exception);
        }
    }

    @Override
    public String confirm(Long orderId) {
        requireServiceToken();
        try {
            OrderStatusBody body = restClient.post()
                    .uri(baseUrl + "/ws/orders/{orderId}/confirm", orderId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(OrderStatusBody.class);
            if (body == null || body.status() == null) {
                throw new OrderApiException("order-api devolveu resposta vazia ao confirmar o pedido " + orderId + ".");
            }
            return body.status();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return null;
            }
            // 404 também é erro: pode indicar rota ou URL errada, e não justifica estorno.
            throw new OrderApiException("order-api respondeu " + exception.getStatusCode().value()
                    + " ao confirmar o pedido " + orderId + ".", exception);
        } catch (RestClientException exception) {
            throw new OrderApiException("order-api indisponível ao confirmar o pedido " + orderId + ".", exception);
        }
    }

    @Override
    public void cancel(Long orderId) {
        requireServiceToken();
        try {
            restClient.post()
                    .uri(baseUrl + "/ws/orders/{orderId}/cancel", orderId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                log.warn("order-api recusou o cancelamento do pedido {} (409); nada a liberar.", orderId);
                return;
            }
            throw new OrderApiException("order-api respondeu " + exception.getStatusCode().value()
                    + " ao cancelar o pedido " + orderId + ".", exception);
        } catch (RestClientException exception) {
            throw new OrderApiException("order-api indisponível ao cancelar o pedido " + orderId + ".", exception);
        }
    }

    private void requireServiceToken() {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new OrderApiException("A autenticação serviço-a-serviço não está configurada (SERVICE_AUTH_TOKEN).");
        }
    }

    private record OrderStatusBody(String status) {
    }
}
