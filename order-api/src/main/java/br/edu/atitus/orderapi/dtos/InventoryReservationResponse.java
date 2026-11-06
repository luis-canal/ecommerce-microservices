package br.edu.atitus.orderapi.dtos;

import java.time.Instant;

public record InventoryReservationResponse(
        Long reservationId,
        Long orderId,
        String status,
        Instant expiresAt
) {
}
