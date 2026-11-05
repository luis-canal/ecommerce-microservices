package br.edu.atitus.productapi.dtos;

public record ProductUpdateRequest(
        String name,
        String brand,
        String model,
        String description,
        String currency,
        double price,
        String image,
        Long subcategoryId
) {
}
