package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CurrencyResponse;
import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceJpaTest {

    private ProductRepository repository;
    private SubcategoryRepository subcategoryRepository;
    private CurrencyClientService currencyClientService;
    private ProductServiceJpa productService;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        subcategoryRepository = mock(SubcategoryRepository.class);
        currencyClientService = mock(CurrencyClientService.class);
        productService = new ProductServiceJpa(repository, currencyClientService, subcategoryRepository);
        ReflectionTestUtils.setField(productService, "serverPort", "8000");
        ReflectionTestUtils.setField(productService, "promotionMessage", "Test promotion");
        when(repository.findByIdAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(1L)).thenReturn(Optional.of(
                new ProductEntity(1L, "Brand", "Model", "Description", "USD", 20.0, "image")
        ));
    }

    @Test
    void convertsPriceUsingCurrencyRate() throws Exception {
        when(currencyClientService.getCurrency("USD", "BRL"))
                .thenReturn(new CurrencyResponse("USD", "BRL", 5.0, "currency-api"));

        var response = productService.findById(1L, "BRL");

        assertEquals(100.0, response.convertedPrice());
        assertTrue(response.environment().contains("currency-api"));
    }

    @Test
    void returnsFallbackPriceAndErrorEnvironmentWhenConversionFails() throws Exception {
        when(currencyClientService.getCurrency("USD", "BRL"))
                .thenThrow(new IllegalStateException("currency unavailable"));

        var response = productService.findById(1L, "BRL");

        assertEquals(-1.0, response.convertedPrice());
        assertTrue(response.environment().contains("ERROR: currency unavailable"));
    }

    @Test
    void hidesProductWhenCategoryOrSubcategoryIsInactive() {
        when(repository.findByIdAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(1L))
                .thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> productService.findById(1L, "BRL"));
    }

    @Test
    void listsProductsUsingActiveSubcategoryAndCategoryFilter() throws Exception {
        when(repository.findAllBySubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of()));

        productService.findAll(PageRequest.of(0, 10), "BRL");

        verify(repository).findAllBySubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(any());
    }

    @Test
    void requiresAnActiveSubcategoryToCreateProduct() {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.save(new ProductRequest(
                        "Brand", "Model", "Description", "BRL", 10.0, null, null
                ))
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(subcategoryRepository, never()).findByIdAndActiveTrueAndCategory_ActiveTrue(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInactiveSubcategoryWhenCreatingProduct() {
        when(subcategoryRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(4L))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.save(new ProductRequest(
                        "Brand", "Model", "Description", "BRL", 10.0, null, 4L
                ))
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void assignsActiveSubcategoryWhenCreatingProduct() throws Exception {
        var category = new CategoryEntity();
        category.setId(2L);
        category.setName("Cuias");
        category.setSlug("cuias");
        var subcategory = new SubcategoryEntity();
        subcategory.setId(4L);
        subcategory.setName("Porongo");
        subcategory.setSlug("porongo");
        subcategory.setCategory(category);
        when(subcategoryRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(4L))
                .thenReturn(Optional.of(subcategory));
        when(repository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var product = productService.save(new ProductRequest(
                "Brand", "Model", "Description", "BRL", 10.0, null, 4L
        ));

        assertEquals(subcategory, product.getSubcategory());
        verify(repository).save(any(ProductEntity.class));
    }
}
