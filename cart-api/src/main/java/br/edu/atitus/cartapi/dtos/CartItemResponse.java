package br.edu.atitus.cartapi.dtos;

import java.math.BigDecimal;
import java.time.Instant;

public record CartItemResponse(
        Long productId,
        String name,
        String brand,
        String model,
        String image,
        String productType,
        Boolean active,
        boolean productAvailable,
        int desiredQuantity,
        long quantityOnHand,
        long quantityReserved,
        long availableQuantity,
        BigDecimal unitPrice,
        BigDecimal estimatedSubtotal,
        boolean available,
        Instant addedAt,
        Instant updatedAt
) {
}
