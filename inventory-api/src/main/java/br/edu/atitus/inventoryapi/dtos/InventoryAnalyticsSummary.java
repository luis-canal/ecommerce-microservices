package br.edu.atitus.inventoryapi.dtos;

public record InventoryAnalyticsSummary(
        long inventoryCount,
        long quantityOnHand,
        long quantityReserved,
        long availableQuantity,
        long outOfStockCount,
        long lowStockCount
) {
}
