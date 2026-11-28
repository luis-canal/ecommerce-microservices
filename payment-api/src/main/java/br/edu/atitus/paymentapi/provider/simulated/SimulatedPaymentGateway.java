package br.edu.atitus.paymentapi.provider.simulated;

import br.edu.atitus.paymentapi.provider.CheckoutSession;
import br.edu.atitus.paymentapi.provider.CheckoutSessionRequest;
import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static br.edu.atitus.paymentapi.provider.PaymentProviderName.SIMULATED_ID_PREFIX;

/**
 * Provedor simulado, somente para demonstração acadêmica: não faz chamadas externas, não cobra
 * nada e aprova todo pagamento assim que a sessão é criada. Não exige credenciais.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private static final String SESSION_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";

    /** PAYMENT_SUCCESS_URL; {@code null} quando não configurada. */
    private final String configuredSuccessUrl;
    private final String fallbackBaseUrl;

    public SimulatedPaymentGateway(
            @Value("${payment.stripe.success-url:}") String successUrl,
            @Value("${payment.simulated.return-base-url:}") String returnBaseUrl
    ) {
        this.configuredSuccessUrl = successUrl == null || successUrl.isBlank() ? null : successUrl.trim();
        this.fallbackBaseUrl = normalizeBaseUrl(returnBaseUrl);
        log.warn("Pagamentos em MODO SIMULADO: nenhum valor é cobrado. Use somente para demonstração.");
    }

    private static String normalizeBaseUrl(String returnBaseUrl) {
        String base = returnBaseUrl == null || returnBaseUrl.isBlank()
                ? "http://localhost:8080"
                : returnBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Ordem de prioridade: PAYMENT_SUCCESS_URL; senão, a rota de retorno no mesmo endereço usado
     * pelo cliente nesta requisição; sem um endereço válido, a base de reserva.
     */
    private String successUrlTemplate() {
        if (configuredSuccessUrl != null) {
            return configuredSuccessUrl;
        }
        return RequestBaseUrl.current().orElse(fallbackBaseUrl)
                + "/payments/return/success?session_id=" + SESSION_PLACEHOLDER;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        String sessionId = SIMULATED_ID_PREFIX + request.paymentId();
        return new CheckoutSession(sessionId, returnUrl(sessionId, request.orderId()));
    }

    /** Mesma URL que o navegador receberia do Stripe, com session_id e order_id. */
    private String returnUrl(String sessionId, Long orderId) {
        String successUrl = successUrlTemplate();
        int fragment = successUrl.indexOf('#');
        String url = fragment < 0 ? successUrl : successUrl.substring(0, fragment);
        String suffix = fragment < 0 ? "" : successUrl.substring(fragment);
        url = url.contains(SESSION_PLACEHOLDER)
                ? url.replace(SESSION_PLACEHOLDER, sessionId)
                : withParameter(url, "session_id", sessionId);
        return withParameter(url, "order_id", orderId.toString()) + suffix;
    }

    private static String withParameter(String url, String name, String value) {
        return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
    }

    @Override
    public Optional<PaymentEvent> immediateResult(String sessionId, Long paymentId) {
        // Sessões de outro provedor (abertas antes da troca de modo) não são aprovadas aqui.
        if (sessionId == null || !sessionId.startsWith(SIMULATED_ID_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(new PaymentEvent(
                SIMULATED_ID_PREFIX + "evt_" + paymentId,
                "simulated.checkout.completed",
                PaymentEventType.CHECKOUT_COMPLETED,
                sessionId,
                paymentId,
                SIMULATED_ID_PREFIX + "pi_" + paymentId,
                true
        ));
    }

    @Override
    public void refund(String paymentIntentId, String idempotencyKey) {
        log.info("Estorno simulado da transação {}: nenhum valor foi cobrado.", paymentIntentId);
    }

    @Override
    public boolean expireSession(String sessionId) {
        return true;
    }

    /** O modo simulado não recebe notificações: aceitar uma permitiria aprovar pagamentos de fora. */
    @Override
    public PaymentEvent parseWebhookEvent(String payload, String signatureHeader) {
        throw new InvalidWebhookSignatureException("O webhook não é usado no modo simulado.");
    }
}
