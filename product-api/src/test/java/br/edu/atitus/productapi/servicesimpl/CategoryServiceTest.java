package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CategoryRequest;
import br.edu.atitus.productapi.dtos.CategoryStatusRequest;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.repositories.CategoryRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CategoryServiceTest {

    private CategoryRepository categoryRepository;
    private SubcategoryRepository subcategoryRepository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        subcategoryRepository = mock(SubcategoryRepository.class);
        service = new CategoryService(categoryRepository, subcategoryRepository, new SlugGenerator());
    }

    @Test
    void listsOnlyActiveCategoriesFromRepository() {
        var category = category(1L, "Erva-mate", true);
        when(categoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(category));

        var categories = service.findActiveCategories();

        assertEquals(1, categories.size());
        assertEquals("Erva-mate", categories.getFirst().name());
        verify(categoryRepository).findAllByActiveTrueOrderByNameAsc();
    }

    @Test
    void listsAllCategoriesIncludingInactiveWithPagination() {
        var activeCategory = category(1L, "Erva-mate", true);
        var inactiveCategory = category(2L, "Cuias", false);
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.findAllByOrderByNameAsc(pageable))
                .thenReturn(new PageImpl<>(List.of(activeCategory, inactiveCategory), pageable, 2));

        var result = service.findAllForAdmin(pageable);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().get(0).active());
        assertFalse(result.getContent().get(1).active());
        verify(categoryRepository).findAllByOrderByNameAsc(pageable);
    }

    @Test
    void createsActiveCategoryWithNormalizedSlug() {
        when(categoryRepository.saveAndFlush(any(CategoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var category = service.create(new CategoryRequest(" Acessórios para chimarrão "));

        assertEquals("Acessórios para chimarrão", category.name());
        assertEquals("acessorios-para-chimarrao", category.slug());
        assertTrue(category.active());
    }

    @Test
    void rejectsDuplicateCategoryNameOrSlug() {
        when(categoryRepository.existsByNameIgnoreCase("Erva-mate")).thenReturn(true);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(new CategoryRequest("Erva-mate"))
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivatesCategoryWithoutChangingSubcategoryStatuses() {
        var category = category(1L, "Erva-mate", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(category)).thenReturn(category);

        var response = service.updateStatus(1L, new CategoryStatusRequest(false));

        assertFalse(response.active());
        verify(subcategoryRepository, never()).findAllByCategory_IdAndActiveTrueOrderByNameAsc(any());
        verify(subcategoryRepository, never()).save(any(SubcategoryEntity.class));
    }

    @Test
    void returnsOnlyActiveSubcategoriesAndRejectsInactiveCategory() {
        var category = category(1L, "Erva-mate", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        var subcategory = new SubcategoryEntity();
        subcategory.setId(5L);
        subcategory.setCategory(category);
        subcategory.setName("Tradicional");
        subcategory.setSlug("tradicional");
        when(subcategoryRepository.findAllByCategory_IdAndActiveTrueOrderByNameAsc(1L))
                .thenReturn(List.of(subcategory));

        var result = service.findActiveSubcategories(1L);

        assertEquals(1, result.size());
        assertEquals("Tradicional", result.getFirst().name());

        category.setActive(false);
        assertThrows(EntityNotFoundException.class, () -> service.findActiveSubcategories(1L));
    }

    @Test
    void listsSubcategoriesForInactiveCategoryIncludingInactiveSubcategories() {
        var inactiveCategory = category(1L, "Erva-mate", false);
        var inactiveSubcategory = new SubcategoryEntity();
        inactiveSubcategory.setId(5L);
        inactiveSubcategory.setCategory(inactiveCategory);
        inactiveSubcategory.setName("Tradicional");
        inactiveSubcategory.setSlug("tradicional");
        inactiveSubcategory.setActive(false);
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(subcategoryRepository.findAllByCategory_IdOrderByNameAsc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(inactiveSubcategory), pageable, 1));

        var result = service.findSubcategoriesForAdmin(1L, pageable);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().getFirst().active());
        assertEquals("Erva-mate", result.getContent().getFirst().categoryName());
        verify(subcategoryRepository).findAllByCategory_IdOrderByNameAsc(1L, pageable);
    }

    @Test
    void returnsNotFoundWhenListingSubcategoriesForMissingCategory() {
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.findSubcategoriesForAdmin(99L, pageable));
        verify(subcategoryRepository, never()).findAllByCategory_IdOrderByNameAsc(99L, pageable);
    }

    private CategoryEntity category(Long id, String name, boolean active) {
        var category = new CategoryEntity();
        category.setId(id);
        category.setName(name);
        category.setSlug(new SlugGenerator().fromName(name));
        category.setActive(active);
        return category;
    }
}
