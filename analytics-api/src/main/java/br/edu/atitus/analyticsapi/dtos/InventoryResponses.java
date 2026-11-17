package br.edu.atitus.analyticsapi.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryResponses {

    private InventoryResponses() {
    }

    public record Summary(
            Instant capturedAt,
            long inventoryCount,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long outOfStockCount,
            long lowStockCount
    ) {
    }

    public record ProductStock(
            Long productId,
            String productType,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long lowStockThreshold,
            boolean lowStock,
            boolean outOfStock,
            Instant updatedAt
    ) {
    }

    public record AvailabilityPoint(
            LocalDate periodStart,
            Instant capturedAt,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long outOfStockCount,
            long lowStockCount
    ) {
    }

    public record MovementTypeTotal(String movementType, long movements, long quantity) {
    }

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

    public record Report(
            SalesResponses.Period period,
            String granularity,
            Summary summary,
            List<ProductStock> outOfStock,
            List<ProductStock> lowStock,
            List<AvailabilityPoint> availabilityHistory,
            List<MovementTypeTotal> movementTotals
    ) {
    }

    public record MovementPage(
            SalesResponses.Period period,
            List<Movement> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
