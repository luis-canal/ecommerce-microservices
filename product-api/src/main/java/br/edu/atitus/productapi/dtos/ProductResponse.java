package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.ProductEntity;

public record ProductResponse(
        Long id,
        String brand,
        String model,
        String description,
        String currency,
        double price,
        String image,
        Long subcategoryId,
        String subcategoryName,
        Long categoryId,
        String categoryName,

        String environment,
        String promotionMessage,
        String targetCurrency,
        double convertedPrice
) {
    public static ProductResponse fromEntity(ProductEntity entity, String environment, String promotionMessage, String targetCurrency, double convertedPrice) {
        var subcategory = entity.getSubcategory();
        var category = subcategory == null ? null : subcategory.getCategory();
        return new ProductResponse(
                entity.getId(),
                entity.getDescription(),
                entity.getBrand(),
                entity.getModel(),
                entity.getCurrency(),
                entity.getPrice(),
                entity.getImage(),
                subcategory == null ? null : subcategory.getId(),
                subcategory == null ? null : subcategory.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                environment,
                promotionMessage,
                targetCurrency,
                convertedPrice
        );
    }
}