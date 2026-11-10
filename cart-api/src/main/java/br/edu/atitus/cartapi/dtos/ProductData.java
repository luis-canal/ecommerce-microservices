package br.edu.atitus.cartapi.dtos;

import java.math.BigDecimal;

public record ProductData(
        Long id,
        String name,
        String brand,
        String model,
        String currency,
        BigDecimal price,
        String image,
        String productType,
        Boolean active,
        BigDecimal convertedPrice
) {
}
