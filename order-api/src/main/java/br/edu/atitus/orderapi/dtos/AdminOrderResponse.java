package br.edu.atitus.orderapi.dtos;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderItemEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.entities.ShippingAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderResponse(
        Long id,
        Long customerId,
        Instant orderDate,
        Instant updatedAt,
        OrderStatus status,
        List<OrderItemEntity> items,
        ShippingAddress shippingAddress,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        String currency,
        Long reservationId,
        Instant reservationExpiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        boolean paymentDataAvailable
) {
    public static AdminOrderResponse fromEntity(OrderEntity order) {
        return new AdminOrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getOrderDate(),
                order.getUpdatedAt(),
                order.getStatus(),
                order.getItems(),
                order.getShippingAddress(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getShippingAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getReservationId(),
                order.getReservationExpiresAt(),
                order.getConfirmedAt(),
                order.getCancelledAt(),
                false
        );
    }
}
