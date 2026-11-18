package br.edu.atitus.paymentapi.provider;

/**
 * Notificação do provedor já validada.
 *
 * @param eventId         identificador do evento (usado na deduplicação)
 * @param rawType         tipo original do evento, apenas para registro
 * @param type            tipo normalizado
 * @param sessionId       sessão de checkout relacionada
 * @param paymentId       id do pagamento enviado na metadata, quando presente
 * @param paymentIntentId transação do provedor, quando disponível
 * @param paid            se o checkout concluído já está pago
 */
public record PaymentEvent(
        String eventId,
        String rawType,
        PaymentEventType type,
        String sessionId,
        Long paymentId,
        String paymentIntentId,
        boolean paid
) {
}
