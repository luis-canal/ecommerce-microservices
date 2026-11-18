package br.edu.atitus.paymentapi.support;

import br.edu.atitus.paymentapi.provider.CheckoutSession;
import br.edu.atitus.paymentapi.provider.CheckoutSessionRequest;
import br.edu.atitus.paymentapi.provider.InvalidWebhookSignatureException;
import br.edu.atitus.paymentapi.provider.PaymentEvent;
import br.edu.atitus.paymentapi.provider.PaymentGateway;
import br.edu.atitus.paymentapi.provider.PaymentProviderException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Provedor em memória: nunca acessa o Stripe. */
public class FakePaymentGateway implements PaymentGateway {

    public final List<CheckoutSessionRequest> createdSessions = new CopyOnWriteArrayList<>();
    public final List<String> refunds = new CopyOnWriteArrayList<>();
    public final List<String> expiredSessions = new CopyOnWriteArrayList<>();

    public volatile boolean failOnCreate;
    public volatile boolean failOnRefund;
    public volatile boolean expireSucceeds = true;
    public volatile long createDelayMs;
    public volatile PaymentEvent nextEvent;

    @Override
    public CheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        if (createDelayMs > 0) {
            try {
                Thread.sleep(createDelayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (failOnCreate) {
            throw new PaymentProviderException("Stripe indisponível (fake).", null);
        }
        createdSessions.add(request);
        String sessionId = "cs_test_" + request.paymentId();
        return new CheckoutSession(sessionId, "https://checkout.stripe.test/" + sessionId);
    }

    @Override
    public void refund(String paymentIntentId, String idempotencyKey) {
        if (failOnRefund) {
            throw new PaymentProviderException("Stripe indisponível (fake).", null);
        }
        refunds.add(paymentIntentId + "|" + idempotencyKey);
    }

    @Override
    public boolean expireSession(String sessionId) {
        expiredSessions.add(sessionId);
        return expireSucceeds;
    }

    @Override
    public PaymentEvent parseWebhookEvent(String payload, String signatureHeader) {
        if (!"valid".equals(signatureHeader) || nextEvent == null) {
            throw new InvalidWebhookSignatureException("Assinatura do webhook inválida.");
        }
        return nextEvent;
    }
}
