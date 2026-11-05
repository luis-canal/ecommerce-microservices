package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.enums.ProductType;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String brand,
        String model,
        String description,
        String currency,
        BigDecimal price,
        String image,
        Long subcategoryId,
        String subcategoryName,
        Long categoryId,
        String categoryName,
        ProductType productType,
        boolean active,

        String environment,
        String promotionMessage,
        String targetCurrency,
        BigDecimal convertedPrice
) {
    public static ProductResponse fromEntity(ProductEntity entity, String environment, String promotionMessage, String targetCurrency, BigDecimal convertedPrice) {
        var subcategory = entity.getSubcategory();
        var category = subcategory == null ? null : subcategory.getCategory();
        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getBrand(),
                entity.getModel(),
                entity.getDescription(),
                entity.getCurrency(),
                entity.getPrice(),
                entity.getImage(),
                subcategory == null ? null : subcategory.getId(),
                subcategory == null ? null : subcategory.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                entity.getProductType(),
                entity.isActive(),
                environment,
                promotionMessage,
                targetCurrency,
                convertedPrice
        );
    }
}