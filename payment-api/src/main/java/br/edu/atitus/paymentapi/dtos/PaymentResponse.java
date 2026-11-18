package br.edu.atitus.paymentapi.dtos;

import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Status do pagamento para o frontend. Não expõe identificadores internos do provedor. */
public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String checkoutUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt
) {
    public static PaymentResponse fromEntity(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus() == PaymentStatus.PENDING ? payment.getCheckoutUrl() : null,
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getConfirmedAt()
        );
    }
}
