package br.edu.atitus.inventoryapi.dtos;

import br.edu.atitus.inventoryapi.entities.InventoryEntity;
import br.edu.atitus.inventoryapi.enums.ProductType;

import java.time.Instant;

public record InventoryResponse(
        Long productId,
        ProductType productType,
        long quantityOnHand,
        long quantityReserved,
        long availableQuantity,
        long lowStockThreshold,
        boolean lowStock,
        Instant createdAt,
        Instant updatedAt
) {
    public static InventoryResponse from(InventoryEntity inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getProductType(),
                inventory.getQuantityOnHand(),
                inventory.getQuantityReserved(),
                inventory.getAvailableQuantity(),
                inventory.getLowStockThreshold(),
                inventory.getQuantityOnHand() <= inventory.getLowStockThreshold(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}
