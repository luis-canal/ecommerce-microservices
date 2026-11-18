package br.edu.atitus.paymentapi.clients;

import java.util.Optional;

/**
 * Contrato interno com o order-api (autenticado por X-Service-Token).
 * Falhas de infraestrutura (indisponível, timeout, 5xx, 404 em confirm/cancel) viram
 * {@link OrderApiException}. As operações de confirm e cancel são idempotentes no order-api.
 */
public interface OrderApiClient {

    /** Dados de pagamento do pedido; vazio quando o pedido não existe. */
    Optional<OrderPaymentInfo> getPaymentInfo(Long orderId);

    /**
     * Pede a confirmação do pedido e devolve o status resultante.
     * Um 409 (pedido não aceita mais confirmação) é devolvido como {@code null}.
     */
    String confirm(Long orderId);

    /** Pede o cancelamento do pedido; 409 (pedido já confirmado) é apenas registrado em log. */
    void cancel(Long orderId);
}
