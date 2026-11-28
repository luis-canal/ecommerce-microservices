package br.edu.atitus.paymentapi.provider;

/**
 * Nomes aceitos em {@code payment.provider}, também devolvidos ao frontend no campo {@code provider}.
 */
public final class PaymentProviderName {

    public static final String STRIPE = "stripe";
    public static final String SIMULATED = "simulated";

    /** Prefixo de todos os identificadores gerados pelo modo simulado. */
    public static final String SIMULATED_ID_PREFIX = "sim_";

    private PaymentProviderName() {
    }

    /**
     * Provedor que criou a sessão, e não o configurado agora: um pagamento antigo continua
     * identificado corretamente depois de uma troca de modo. Sem sessão, devolve {@code null}.
     */
    public static String ofSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionId.startsWith(SIMULATED_ID_PREFIX) ? SIMULATED : STRIPE;
    }
}
