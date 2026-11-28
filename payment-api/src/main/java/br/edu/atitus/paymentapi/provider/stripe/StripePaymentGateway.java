package br.edu.atitus.paymentapi.provider.stripe;

import br.edu.atitus.paymentapi.provider.CheckoutSession;
import br.edu.atitus.paymentapi.provider.CheckoutSessionRequest;
import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentEventType;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.provider.PaymentProviderException;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Único ponto do serviço que conhece o SDK do Stripe.
 * Criado somente com {@code payment.provider=stripe}. O construtor valida a configuração:
 * nesse modo, sem chave de teste a aplicação não sobe.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    /** Mínimo aceito pelo Stripe é 30 min; a folga cobre latência e diferença de relógio. */
    static final Duration SESSION_LIFETIME = Duration.ofMinutes(30).plusSeconds(60);

    /** Código de erro do Stripe quando a cobrança já foi totalmente estornada. */
    static final String ALREADY_REFUNDED = "charge_already_refunded";

    private final StripeClient client;
    private final String webhookSecret;
    private final String successUrl;
    private final String cancelUrl;

    @Autowired
    public StripePaymentGateway(
            @Value("${payment.stripe.secret-key:}") String secretKey,
            @Value("${payment.stripe.webhook-secret:}") String webhookSecret,
            @Value("${payment.stripe.success-url:}") String successUrl,
            @Value("${payment.stripe.cancel-url:}") String cancelUrl,
            @Value("${payment.stripe.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${payment.stripe.read-timeout-ms:20000}") int readTimeoutMs
    ) {
        this(StripeClient.builder()
                        .setApiKey(validateConfiguration(secretKey, webhookSecret, successUrl, cancelUrl))
                        .setConnectTimeout(connectTimeoutMs)
                        .setReadTimeout(readTimeoutMs)
                        .setMaxNetworkRetries(2)
                        .build(),
                webhookSecret, successUrl, cancelUrl);
    }

    /** Usado nos testes para apontar o cliente para um servidor local. */
    StripePaymentGateway(StripeClient client, String webhookSecret, String successUrl, String cancelUrl) {
        this.client = client;
        this.webhookSecret = webhookSecret;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    /** Valida a configuração e devolve a chave, para uso no construtor. */
    static String validateConfiguration(String secretKey, String webhookSecret, String successUrl, String cancelUrl) {
        // Nunca inclua o valor da chave nas mensagens.
        if (secretKey == null || !(secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_"))) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY ausente ou não é de teste: use uma chave sk_test_ ou rk_test_.");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET não configurado.");
        }
        if (successUrl == null || !successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            throw new IllegalStateException("PAYMENT_SUCCESS_URL deve conter {CHECKOUT_SESSION_ID}.");
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_CANCEL_URL não configurado.");
        }
        return secretKey;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        Map<String, String> metadata = Map.of(
                "payment_id", request.paymentId().toString(),
                "order_id", request.orderId().toString(),
                "customer_id", request.customerId().toString()
        );
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .setSuccessUrl(withOrderId(successUrl, request.orderId()))
                .setCancelUrl(withOrderId(cancelUrl, request.orderId()))
                .setClientReferenceId(request.orderId().toString())
                .setExpiresAt(Instant.now().plus(SESSION_LIFETIME).getEpochSecond())
                .putAllMetadata(metadata)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putAllMetadata(metadata)
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.currency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(toMinorUnits(request.amount()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Pedido #" + request.orderId())
                                        .build())
                                .build())
                        .build())
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("payment-" + request.paymentId())
                .build();
        try {
            Session session = client.v1().checkout().sessions().create(params, options);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            throw new PaymentProviderException("Falha ao criar a sessão de checkout no Stripe.", exception);
        }
    }

    /**
     * Acrescenta order_id à URL de retorno. O Stripe só substitui {CHECKOUT_SESSION_ID};
     * com o order_id o app consulta o pagamento sem precisar guardar o pedido antes do checkout.
     */
    static String withOrderId(String url, Long orderId) {
        int fragment = url.indexOf('#');
        String base = fragment < 0 ? url : url.substring(0, fragment);
        String suffix = fragment < 0 ? "" : url.substring(fragment);
        return base + (base.contains("?") ? "&" : "?") + "order_id=" + orderId + suffix;
    }

    @Override
    public void refund(String paymentIntentId, String idempotencyKey) {
        RefundCreateParams params = RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
        try {
            client.v1().refunds().create(params, options);
        } catch (InvalidRequestException exception) {
            if (!ALREADY_REFUNDED.equals(exception.getCode())) {
                throw new PaymentProviderException("Falha ao estornar o pagamento no Stripe.", exception);
            }
            // Já totalmente estornado (ex.: reenvio após o estorno ter sido feito): é o resultado desejado.
            log.info("Transação já estava totalmente estornada no Stripe; estorno considerado concluído.");
        } catch (StripeException exception) {
            throw new PaymentProviderException("Falha ao estornar o pagamento no Stripe.", exception);
        }
    }

    @Override
    public boolean expireSession(String sessionId) {
        try {
            client.v1().checkout().sessions().expire(sessionId);
            return true;
        } catch (InvalidRequestException exception) {
            // A sessão não está mais aberta: descobre se expirou ou se foi concluída pelo cliente.
            try {
                String status = client.v1().checkout().sessions().retrieve(sessionId).getStatus();
                if ("expired".equals(status)) {
                    return true;
                }
                if ("complete".equals(status)) {
                    return false;
                }
            } catch (StripeException retrieveException) {
                throw new PaymentProviderException("Falha ao consultar a sessão no Stripe.", retrieveException);
            }
            throw new PaymentProviderException("Não foi possível expirar a sessão no Stripe.", exception);
        } catch (StripeException exception) {
            throw new PaymentProviderException("Falha ao expirar a sessão no Stripe.", exception);
        }
    }

    @Override
    public PaymentEvent parseWebhookEvent(String payload, String signatureHeader) {
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("Assinatura do webhook ausente.");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException exception) {
            throw new InvalidWebhookSignatureException("Assinatura do webhook inválida.");
        } catch (RuntimeException exception) {
            // Corpo que não é JSON de evento válido.
            throw new InvalidWebhookSignatureException("Corpo do webhook inválido.");
        }

        PaymentEventType type = switch (event.getType()) {
            case "checkout.session.completed" -> PaymentEventType.CHECKOUT_COMPLETED;
            case "checkout.session.async_payment_succeeded" -> PaymentEventType.ASYNC_PAYMENT_SUCCEEDED;
            case "checkout.session.async_payment_failed" -> PaymentEventType.ASYNC_PAYMENT_FAILED;
            case "checkout.session.expired" -> PaymentEventType.SESSION_EXPIRED;
            default -> PaymentEventType.IGNORED;
        };
        if (type == PaymentEventType.IGNORED) {
            return new PaymentEvent(event.getId(), event.getType(), type, null, null, null, false);
        }

        Session session = readSession(event);
        return new PaymentEvent(
                event.getId(),
                event.getType(),
                type,
                session.getId(),
                parsePaymentId(session.getMetadata()),
                session.getPaymentIntent(),
                "paid".equals(session.getPaymentStatus())
        );
    }

    private Session readSession(Event event) {
        // getObject() fica vazio quando a versão da API do evento difere da do SDK;
        // os campos usados aqui são estáveis entre versões.
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (object == null) {
            try {
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException exception) {
                throw new IllegalStateException("Não foi possível ler a sessão do evento " + event.getId() + ".");
            }
        }
        if (!(object instanceof Session session)) {
            throw new IllegalStateException("Evento " + event.getId() + " não contém uma sessão de checkout.");
        }
        return session;
    }

    private static Long parsePaymentId(Map<String, String> metadata) {
        String value = metadata == null ? null : metadata.get("payment_id");
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            log.warn("metadata.payment_id inválido no evento do Stripe.");
            return null;
        }
    }

    /** Converte para a menor unidade da moeda (centavos), sem arredondar. */
    static long toMinorUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor do pagamento deve ser positivo.");
        }
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("O valor do pagamento tem mais de duas casas decimais.");
        }
    }
}
