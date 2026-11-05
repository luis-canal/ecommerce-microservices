package br.edu.atitus.productapi.dtos;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String name,
        String brand,
        String model,
        String description,
        String currency,
        BigDecimal price,
        String image,
        Long subcategoryId
) {
}
