package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.CategoryEntity;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse fromEntity(CategoryEntity category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
