package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.CategoryService;
import br.edu.atitus.productapi.servicesimpl.SubcategoryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

class CategoryControllerTest {

    private CategoryService categoryService;
    private SubcategoryService subcategoryService;
    private MockMvc categoryMvc;
    private MockMvc subcategoryMvc;

    @BeforeEach
    void setUp() {
        categoryService = mock(CategoryService.class);
        subcategoryService = mock(SubcategoryService.class);
        var authorizationService = new AuthorizationService();
        categoryMvc = standaloneSetup(
                new CategoryController(categoryService),
                new WsCategoryController(categoryService, authorizationService)
        ).setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        subcategoryMvc = standaloneSetup(
                new WsSubcategoryController(subcategoryService, authorizationService)
        ).setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @Test
    void publicCategoriesEndpointListsActiveCategories() throws Exception {
        when(categoryService.findActiveCategories()).thenReturn(java.util.List.of());

        categoryMvc.perform(get("/categories"))
                .andExpect(status().isOk());

        verify(categoryService).findActiveCategories();
    }

    @Test
    void publicSubcategoryEndpointReturnsNotFoundForMissingOrInactiveCategory() throws Exception {
        when(categoryService.findActiveSubcategories(8L))
                .thenThrow(new EntityNotFoundException("Categoria não encontrada"));

        categoryMvc.perform(get("/categories/8/subcategories"))
                .andExpect(status().isNotFound());
    }

    @Test
    void administrativeCategoryAndSubcategoryListsArePaginated() throws Exception {
        when(categoryService.findAllForAdmin(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(categoryService.findSubcategoriesForAdmin(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(subcategoryService.findAllForAdmin(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        categoryMvc.perform(get("/ws/categories")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,desc")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());
        categoryMvc.perform(get("/ws/categories/8/subcategories")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,desc")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());
        subcategoryMvc.perform(get("/ws/subcategories")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,desc")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());

        var pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "name"));
        verify(categoryService).findAllForAdmin(pageable);
        verify(categoryService).findSubcategoriesForAdmin(8L, pageable);
        verify(subcategoryService).findAllForAdmin(pageable);
    }

    @Test
    void administrativeCategoryAndSubcategoryListsRequireAdmin() throws Exception {
        categoryMvc.perform(get("/ws/categories").header("X-User-Type", 2))
                .andExpect(status().isForbidden());
        categoryMvc.perform(get("/ws/categories/8/subcategories").header("X-User-Type", 2))
                .andExpect(status().isForbidden());
        subcategoryMvc.perform(get("/ws/subcategories").header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).findAllForAdmin(org.mockito.ArgumentMatchers.any());
        verify(categoryService, never()).findSubcategoriesForAdmin(
                org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.any()
        );
        verify(subcategoryService, never()).findAllForAdmin(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void categoryAdministrationRequiresAdminUser() throws Exception {
        categoryMvc.perform(post("/ws/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Categoria\"}")
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subcategoryAdministrationRequiresAdminUser() throws Exception {
        subcategoryMvc.perform(post("/ws/subcategories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"name\":\"Subcategoria\"}")
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(subcategoryService, never()).create(org.mockito.ArgumentMatchers.any());
    }
}
