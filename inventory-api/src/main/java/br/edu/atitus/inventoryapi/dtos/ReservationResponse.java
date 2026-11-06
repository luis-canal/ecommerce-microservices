package br.edu.atitus.inventoryapi.dtos;

import br.edu.atitus.inventoryapi.entities.InventoryReservationEntity;
import br.edu.atitus.inventoryapi.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long orderId,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant confirmedAt,
        Instant releasedAt,
        Instant expiredAt,
        List<ReservationItemResponse> items
) {
    public static ReservationResponse from(
            InventoryReservationEntity reservation,
            List<ReservationItemResponse> items
    ) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getReleasedAt(),
                reservation.getExpiredAt(),
                items
        );
    }
}
