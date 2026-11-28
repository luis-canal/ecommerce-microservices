package br.edu.atitus.paymentapi.dtos;

import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import br.edu.atitus.paymentapi.provider.PaymentProviderName;

public record CheckoutSessionResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        String checkoutUrl,
        String provider
) {
    public static CheckoutSessionResponse fromEntity(PaymentEntity payment) {
        return new CheckoutSessionResponse(
                payment.getId(), payment.getOrderId(), payment.getStatus(), payment.getCheckoutUrl(),
                PaymentProviderName.ofSession(payment.getStripeCheckoutSessionId()));
    }
}
