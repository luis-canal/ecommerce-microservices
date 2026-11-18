package br.edu.atitus.paymentapi.provider;

/**
 * Fronteira com o provedor de pagamento. Somente a implementação conhece o SDK do provedor;
 * o restante do serviço (e o order-api) não sabe qual provedor está em uso.
 *
 * Falhas de comunicação ou do provedor são lançadas como {@link PaymentProviderException}.
 */
public interface PaymentGateway {

    CheckoutSession createCheckoutSession(CheckoutSessionRequest request);

    /** Estorna o pagamento inteiro. A chave de idempotência evita estorno duplicado em reenvios. */
    void refund(String paymentIntentId, String idempotencyKey);

    /**
     * Expira uma sessão ainda aberta. Retorna {@code false} quando a sessão já foi concluída
     * pelo cliente (pagamento pago ou em processamento) e, portanto, não pode mais ser expirada.
     */
    boolean expireSession(String sessionId);

    /** Valida a assinatura e converte a notificação recebida no webhook. */
    PaymentEvent parseWebhookEvent(String payload, String signatureHeader);
}
