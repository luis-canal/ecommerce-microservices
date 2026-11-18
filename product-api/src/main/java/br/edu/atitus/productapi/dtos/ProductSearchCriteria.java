package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.enums.ProductStatusFilter;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String name,
        Long categoryId,
        Long subcategoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatusFilter status
) {
    public ProductSearchCriteria {
        if (status == null) {
            status = ProductStatusFilter.ACTIVE;
        }
    }

    /** Sem status explícito, a busca considera somente produtos ativos (comportamento do catálogo público). */
    public ProductSearchCriteria(
            String name,
            Long categoryId,
            Long subcategoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        this(name, categoryId, subcategoryId, minPrice, maxPrice, ProductStatusFilter.ACTIVE);
    }
}
