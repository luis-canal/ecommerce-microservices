package br.edu.atitus.paymentapi.provider;

/** Falha ao falar com o provedor de pagamento (indisponível, timeout ou requisição rejeitada). */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
