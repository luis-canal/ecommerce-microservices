package br.edu.atitus.paymentapi.dtos;

import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;

public record CheckoutSessionResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        String checkoutUrl
) {
    public static CheckoutSessionResponse fromEntity(PaymentEntity payment) {
        return new CheckoutSessionResponse(
                payment.getId(), payment.getOrderId(), payment.getStatus(), payment.getCheckoutUrl());
    }
}
