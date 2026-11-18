package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.enums.ProductType;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String brand,
        String model,
        String description,
        String currency,
        BigDecimal price,
        String image,
        List<ProductImageResponse> images,
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
    public static ProductResponse fromEntity(ProductEntity entity, List<ProductImageResponse> images, String environment, String promotionMessage, String targetCurrency, BigDecimal convertedPrice) {
        var subcategory = entity.getSubcategory();
        var category = subcategory == null ? null : subcategory.getCategory();
        List<ProductImageResponse> productImages = images == null ? List.of() : List.copyOf(images);
        return new ProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getBrand(),
                entity.getModel(),
                entity.getDescription(),
                entity.getCurrency(),
                entity.getPrice(),
                resolveImage(entity.getImage(), productImages),
                productImages,
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

    /**
     * Mantém a compatibilidade do campo image: URL da imagem principal; sem imagens,
     * o valor legado da coluna image; caso contrário, null.
     */
    public static String resolveImage(String legacyImage, List<ProductImageResponse> images) {
        if (images != null) {
            for (var image : images) {
                if (image.isPrimary()) {
                    return image.url();
                }
            }
        }
        return legacyImage;
    }
}
