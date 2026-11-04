package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.CategoryService;
import br.edu.atitus.productapi.servicesimpl.SubcategoryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        ).build();
        subcategoryMvc = standaloneSetup(
                new WsSubcategoryController(subcategoryService, authorizationService)
        ).build();
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
