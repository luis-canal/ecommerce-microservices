package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.enums.ProductType;

public record InventoryProductData(Long productId, ProductType productType, boolean active) {
}
