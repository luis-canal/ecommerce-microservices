package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.SubcategoryEntity;

import java.time.Instant;

public record SubcategoryResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubcategoryResponse fromEntity(SubcategoryEntity subcategory) {
        return new SubcategoryResponse(
                subcategory.getId(),
                subcategory.getCategory().getId(),
                subcategory.getCategory().getName(),
                subcategory.getName(),
                subcategory.getSlug(),
                subcategory.isActive(),
                subcategory.getCreatedAt(),
                subcategory.getUpdatedAt()
        );
    }
}
