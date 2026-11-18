package br.edu.atitus.orderapi.dtos;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato interno mínimo consumido pelo payment-api: apenas o necessário para validar
 * o dono do pedido, o valor a cobrar e a validade da reserva.
 */
public record PaymentInfoResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant reservationExpiresAt
) {
    public static PaymentInfoResponse fromEntity(OrderEntity order) {
        return new PaymentInfoResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getReservationExpiresAt()
        );
    }
}
