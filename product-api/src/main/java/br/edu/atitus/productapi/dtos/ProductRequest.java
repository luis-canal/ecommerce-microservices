package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.enums.ProductType;

public record ProductRequest(
        String name,
        String brand,
        String model,
        String description,
        String currency,
        double price,
        String image,
        Long subcategoryId,
        ProductType productType
) {
}