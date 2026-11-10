package br.edu.atitus.cartapi.dtos;

public record InventoryData(
        Long productId,
        String productType,
        long quantityOnHand,
        long quantityReserved,
        long availableQuantity
) {
}
