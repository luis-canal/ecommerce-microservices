package br.edu.atitus.paymentapi.provider;

import java.math.BigDecimal;

public record CheckoutSessionRequest(
        Long paymentId,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        String currency
) {
}
