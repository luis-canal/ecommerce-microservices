package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.enums.ProductType;

import java.math.BigDecimal;

public record ProductRequest(
        String name,
        String brand,
        String model,
        String description,
        String currency,
        BigDecimal price,
        String image,
        Long subcategoryId,
        ProductType productType
) {
}