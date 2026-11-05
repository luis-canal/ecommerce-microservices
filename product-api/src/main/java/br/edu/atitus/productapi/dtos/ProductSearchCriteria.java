package br.edu.atitus.productapi.dtos;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String name,
        Long categoryId,
        Long subcategoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
