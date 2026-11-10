package br.edu.atitus.cartapi.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        Long id,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        String currency,
        BigDecimal estimatedSubtotal,
        boolean estimateComplete,
        List<CartItemResponse> items
) {
}
