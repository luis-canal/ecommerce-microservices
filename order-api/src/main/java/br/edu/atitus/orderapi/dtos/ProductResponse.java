package br.edu.atitus.orderapi.dtos;

import br.edu.atitus.orderapi.entities.OrderItemEntity;

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
        String productType,
        Boolean active,
        String environment,
        String promotionMessage,
        String targetCurrency,
        BigDecimal convertedPrice
) {
    public static ProductResponse fromSnapshot(OrderItemEntity item, String targetCurrency, BigDecimal convertedPrice) {
        return new ProductResponse(
                item.getProductId(),
                item.getProductName(),
                item.getProductBrand(),
                item.getProductModel(),
                item.getProductDescription(),
                item.getCatalogCurrencyAtPurchase() == null
                        ? item.getCurrencyAtPurchase()
                        : item.getCatalogCurrencyAtPurchase(),
                item.getCatalogPriceAtPurchase() == null
                        ? item.getPriceAtPurchase()
                        : item.getCatalogPriceAtPurchase(),
                item.getProductImage(),
                item.getSubcategoryId(),
                item.getSubcategoryName(),
                item.getCategoryId(),
                item.getCategoryName(),
                item.getProductType(),
                item.getProductActive(),
                item.getProductEnvironment(),
                item.getPromotionMessage(),
                targetCurrency,
                convertedPrice
        );
    }
}
