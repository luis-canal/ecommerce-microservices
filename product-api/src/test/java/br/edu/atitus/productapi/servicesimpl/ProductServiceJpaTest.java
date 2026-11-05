package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CurrencyResponse;
import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.enums.ProductType;
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
        when(repository.findByIdAndActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(1L))
                .thenReturn(Optional.of(product()));
    }

    @Test
    void convertsPriceUsingCurrencyRate() throws Exception {
        when(currencyClientService.getCurrency("USD", "BRL"))
                .thenReturn(new CurrencyResponse("USD", "BRL", 5.0, "currency-api"));

        var response = productService.findById(1L, "BRL");

        assertEquals("Name", response.name());
        assertEquals("Brand", response.brand());
        assertEquals("Model", response.model());
        assertEquals("Description", response.description());
        assertEquals(ProductType.SINGLE_UNIT, response.productType());
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
        when(repository.findByIdAndActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(1L))
                .thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> productService.findById(1L, "BRL"));
    }

    @Test
    void listsProductsUsingActiveSubcategoryAndCategoryFilter() throws Exception {
        when(repository.findAllByActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of()));

        productService.findAll(PageRequest.of(0, 10), "BRL");

        verify(repository).findAllByActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(any());
    }

    @Test
    void requiresAnActiveSubcategoryToCreateProduct() {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.save(new ProductRequest(
                        "Name", "Brand", "Model", "Description", "BRL", 10.0, null, null, ProductType.MULTI_UNIT
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
                        "Name", "Brand", "Model", "Description", "BRL", 10.0, null, 4L, ProductType.MULTI_UNIT
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
                "Name", "Brand", "Model", "Description", "BRL", 10.0, null, 4L, ProductType.SINGLE_UNIT
        ));

        assertEquals("Name", product.getName());
        assertEquals(ProductType.SINGLE_UNIT, product.getProductType());
        assertEquals(subcategory, product.getSubcategory());
        verify(repository).save(any(ProductEntity.class));
    }

    @Test
    void requiresNameAndProductTypeWhenCreatingProduct() {
        var noName = assertThrows(
                ResponseStatusException.class,
                () -> productService.save(new ProductRequest(
                        " ", "Brand", "Model", "Description", "BRL", 10.0, null, 4L, ProductType.MULTI_UNIT
                ))
        );
        var noType = assertThrows(
                ResponseStatusException.class,
                () -> productService.save(new ProductRequest(
                        "Name", "Brand", "Model", "Description", "BRL", 10.0, null, 4L, null
                ))
        );

        assertEquals(400, noName.getStatusCode().value());
        assertEquals(400, noType.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void updatesEditableFieldsWithoutChangingProductTypeOrStatus() {
        var product = product();
        product.setActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        var newSubcategory = activeSubcategory();
        when(subcategoryRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(5L))
                .thenReturn(Optional.of(newSubcategory));
        when(repository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = productService.update(1L, new ProductUpdateRequest(
                "Updated name", "Updated brand", "Updated model", "Updated description",
                "EUR", 15.0, "new-image", 5L
        ));

        assertEquals(1L, updated.getId());
        assertEquals("Updated name", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        assertEquals(ProductType.SINGLE_UNIT, updated.getProductType());
        assertEquals(false, updated.isActive());
        assertEquals(newSubcategory, updated.getSubcategory());
    }

    @Test
    void rejectsUpdateWhenSubcategoryIsInactiveOrMissing() {
        when(repository.findById(1L)).thenReturn(Optional.of(product()));
        when(subcategoryRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(5L)).thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.update(1L, new ProductUpdateRequest(
                        "Name", "Brand", "Model", "Description", "BRL", 10.0, null, 5L
                ))
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void reportsMissingProductBeforeValidatingUpdateRequest() {
        var exception = assertThrows(
                jakarta.persistence.EntityNotFoundException.class,
                () -> productService.update(99L, null)
        );

        assertEquals("Produto não encontrado", exception.getMessage());
    }

    @Test
    void preventsReactivationWhenCategoryOrSubcategoryIsInactive() {
        var product = product();
        var category = new CategoryEntity();
        category.setActive(false);
        var subcategory = new SubcategoryEntity();
        subcategory.setActive(true);
        subcategory.setCategory(category);
        product.setSubcategory(subcategory);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.updateStatus(1L, new ProductStatusRequest(true))
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void deactivatesProductWithoutDeletingIt() {
        var product = product();
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = productService.updateStatus(1L, new ProductStatusRequest(false));

        assertEquals(false, updated.isActive());
        verify(repository).save(product);
    }

    @Test
    void reactivatesProductWhenCategoryAndSubcategoryAreActive() {
        var product = product();
        product.setActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = productService.updateStatus(1L, new ProductStatusRequest(true));

        assertEquals(true, updated.isActive());
        verify(repository).save(product);
    }

    private ProductEntity product() {
        var product = new ProductEntity();
        product.setId(1L);
        product.setName("Name");
        product.setBrand("Brand");
        product.setModel("Model");
        product.setDescription("Description");
        product.setCurrency("USD");
        product.setPrice(20.0);
        product.setImage("image");
        product.setProductType(ProductType.SINGLE_UNIT);
        product.setSubcategory(activeSubcategory());
        return product;
    }

    private SubcategoryEntity activeSubcategory() {
        var category = new CategoryEntity();
        category.setActive(true);
        var subcategory = new SubcategoryEntity();
        subcategory.setActive(true);
        subcategory.setCategory(category);
        return subcategory;
    }
}
