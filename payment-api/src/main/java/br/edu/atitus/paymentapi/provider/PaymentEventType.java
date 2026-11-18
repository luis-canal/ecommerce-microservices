package br.edu.atitus.paymentapi.provider;

public enum PaymentEventType {
    /** Checkout concluído; pode estar pago ou ainda em processamento. */
    CHECKOUT_COMPLETED,
    /** Pagamento assíncrono aprovado depois do checkout. */
    ASYNC_PAYMENT_SUCCEEDED,
    /** Pagamento assíncrono recusado depois do checkout. */
    ASYNC_PAYMENT_FAILED,
    /** Sessão expirou sem pagamento. */
    SESSION_EXPIRED,
    /** Qualquer outro evento: registrado e ignorado. */
    IGNORED
}
