package br.edu.atitus.inventoryapi.dtos;

import br.edu.atitus.inventoryapi.entities.InventoryMovementEntity;
import br.edu.atitus.inventoryapi.enums.MovementType;

import java.time.Instant;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        MovementType movementType,
        long quantityDelta,
        String reason,
        Long orderId,
        Long reservationId,
        Instant createdAt
) {
    public static InventoryMovementResponse from(InventoryMovementEntity movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getProductId(),
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getReason(),
                movement.getOrderId(),
                movement.getReservationId(),
                movement.getCreatedAt()
        );
    }
}
