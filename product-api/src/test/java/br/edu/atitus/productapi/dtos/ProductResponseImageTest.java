package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.ProductEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductResponseImageTest {

    @Test
    void imageIsThePrimaryUrlWhenThereAreImages() {
        var response = ProductResponse.fromEntity(product("legado.jpg"), List.of(
                new ProductImageResponse(1L, "https://cdn/a.png", false, 1),
                new ProductImageResponse(2L, "https://cdn/b.png", true, 2)
        ), null, null, "BRL", BigDecimal.TEN);

        assertEquals("https://cdn/b.png", response.image());
        assertEquals(2, response.images().size());
    }

    @Test
    void imageFallsBackToLegacyColumnWithoutImages() {
        var response = ProductResponse.fromEntity(product("legado.jpg"), List.of(), null, null, "BRL", BigDecimal.TEN);

        assertEquals("legado.jpg", response.image());
        assertEquals(List.of(), response.images());
    }

    @Test
    void imageIsNullWithoutImagesOrLegacyValue() {
        var response = ProductResponse.fromEntity(product(null), null, null, null, "BRL", BigDecimal.TEN);

        assertNull(response.image());
        assertEquals(List.of(), response.images());
    }

    @Test
    void publicResponseKeepsEveryOldKeyAndOnlyAddsImages() {
        // Chaves do ProductResponse antes da funcionalidade de imagens.
        var oldKeys = java.util.List.of("id", "name", "brand", "model", "description", "currency", "price", "image",
                "subcategoryId", "subcategoryName", "categoryId", "categoryName", "productType", "active",
                "environment", "promotionMessage", "targetCurrency", "convertedPrice");
        var json = tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(
                ProductResponse.fromEntity(product("legado.jpg"), List.of(), null, null, "BRL", BigDecimal.TEN));

        var newKeys = new java.util.ArrayList<String>();
        json.properties().forEach(entry -> newKeys.add(entry.getKey()));
        var expected = new java.util.ArrayList<>(oldKeys);
        expected.add(expected.indexOf("image") + 1, "images");
        assertEquals(expected, newKeys);
    }

    private static ProductEntity product(String image) {
        var product = new ProductEntity();
        product.setId(1L);
        product.setImage(image);
        product.setPrice(BigDecimal.TEN);
        product.setCurrency("BRL");
        return product;
    }
}
