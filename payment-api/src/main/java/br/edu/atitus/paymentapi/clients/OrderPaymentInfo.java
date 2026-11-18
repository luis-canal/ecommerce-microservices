package br.edu.atitus.paymentapi.clients;

import java.math.BigDecimal;
import java.time.Instant;

/** Resposta de GET /ws/orders/internal/{orderId}/payment-info. */
public record OrderPaymentInfo(
        Long id,
        Long customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant reservationExpiresAt
) {
}
