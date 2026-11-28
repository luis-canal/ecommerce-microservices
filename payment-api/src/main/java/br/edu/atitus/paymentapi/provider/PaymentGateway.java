package br.edu.atitus.paymentapi.provider;

import java.util.Optional;

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

    /**
     * Resultado já conhecido logo após a criação da sessão, processado pelo mesmo caminho do
     * webhook. Vazio (padrão) quando o resultado só chega depois pelo webhook, como no Stripe.
     */
    default Optional<PaymentEvent> immediateResult(String sessionId, Long paymentId) {
        return Optional.empty();
    }
}
