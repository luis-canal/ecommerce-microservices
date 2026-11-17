package br.edu.atitus.analyticsapi.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Contratos dos endpoints GET /ws/inventory/analytics/** (inventory-api). */
public final class SourceInventory {

    private SourceInventory() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            long inventoryCount,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long outOfStockCount,
            long lowStockCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long productId,
            String productType,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long lowStockThreshold,
            boolean lowStock,
            Instant updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Movement(
            Long id,
            Long productId,
            String movementType,
            long quantityDelta,
            String reason,
            Long orderId,
            Long reservationId,
            Instant createdAt
    ) {
    }
}
