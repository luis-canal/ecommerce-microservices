package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.enums.ProductType;

import java.math.BigDecimal;

public record CartProductData(
        Long id,
        String name,
        String brand,
        String model,
        String image,
        String currency,
        BigDecimal price,
        BigDecimal convertedPrice,
        ProductType productType,
        boolean active
) {
}
