package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.SubcategoryRequest;
import br.edu.atitus.productapi.dtos.SubcategoryStatusRequest;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.repositories.CategoryRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubcategoryServiceTest {

    private CategoryRepository categoryRepository;
    private SubcategoryRepository subcategoryRepository;
    private SubcategoryService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        subcategoryRepository = mock(SubcategoryRepository.class);
        service = new SubcategoryService(categoryRepository, subcategoryRepository, new SlugGenerator());
    }

    @Test
    void createsSubcategoryWithSlugOnlyUnderActiveCategory() {
        var category = category(1L, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(subcategoryRepository.saveAndFlush(any(SubcategoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new SubcategoryRequest(1L, "Aço inoxidável"));

        assertEquals(1L, result.categoryId());
        assertEquals("aco-inoxidavel", result.slug());
        assertTrue(result.active());
    }

    @Test
    void rejectsMissingOrInactiveParentCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        var missing = assertThrows(
                ResponseStatusException.class,
                () -> service.create(new SubcategoryRequest(1L, "Tradicional"))
        );
        assertEquals(400, missing.getStatusCode().value());

        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, false)));
        var inactive = assertThrows(
                ResponseStatusException.class,
                () -> service.create(new SubcategoryRequest(2L, "Tradicional"))
        );
        assertEquals(400, inactive.getStatusCode().value());
        verify(subcategoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateNameOrSlugWithinCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, true)));
        when(subcategoryRepository.existsByCategory_IdAndNameIgnoreCase(1L, "Tereré"))
                .thenReturn(true);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(new SubcategoryRequest(1L, "Tereré"))
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(subcategoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNotActivateSubcategoryWhenParentCategoryIsInactive() {
        var subcategory = new SubcategoryEntity();
        subcategory.setId(3L);
        subcategory.setCategory(category(1L, false));
        subcategory.setActive(false);
        when(subcategoryRepository.findById(3L)).thenReturn(Optional.of(subcategory));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStatus(3L, new SubcategoryStatusRequest(true))
        );

        assertEquals(409, exception.getStatusCode().value());
        assertFalse(subcategory.isActive());
        verify(subcategoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingSubcategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, true)));
        when(subcategoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.update(10L, new SubcategoryRequest(1L, "Nova"))
        );
    }

    private CategoryEntity category(Long id, boolean active) {
        var category = new CategoryEntity();
        category.setId(id);
        category.setName("Erva-mate");
        category.setSlug("erva-mate");
        category.setActive(active);
        return category;
    }
}
