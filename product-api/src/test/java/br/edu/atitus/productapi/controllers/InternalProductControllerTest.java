package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.CurrencyResponse;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.enums.ProductType;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.servicesimpl.CurrencyClientService;
import br.edu.atitus.productapi.servicesimpl.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalProductControllerTest {

    @Test
    void returnsInactiveProductDetailsAndCurrentBrlPriceToTrustedServices() {
        ProductRepository productRepository = mock(ProductRepository.class);
        CurrencyClientService currencyClientService = mock(CurrencyClientService.class);
        ProductEntity product = new ProductEntity();
        product.setId(17L);
        product.setName("Cuia artesanal");
        product.setBrand("Marca");
        product.setModel("CU-01");
        product.setCurrency("USD");
        product.setPrice(new BigDecimal("10.00"));
        product.setProductType(ProductType.MULTI_UNIT);
        product.setActive(false);
        CategoryEntity category = new CategoryEntity();
        category.setActive(true);
        SubcategoryEntity subcategory = new SubcategoryEntity();
        subcategory.setCategory(category);
        subcategory.setActive(true);
        product.setSubcategory(subcategory);
        when(productRepository.findCartDataById(17L)).thenReturn(Optional.of(product));
        when(currencyClientService.getCurrency("USD", "BRL"))
                .thenReturn(new CurrencyResponse("USD", "BRL", 5.25, "test"));
        InternalProductController controller =
                new InternalProductController(
                        productRepository, currencyClientService, mock(ProductImageService.class), "service-secret");

        var response = controller.getCartData(17L, "service-secret").getBody();

        assertEquals(false, response.active());
        assertEquals(new BigDecimal("52.50"), response.convertedPrice());
        assertEquals("Cuia artesanal", response.name());
    }

    @Test
    void cartDataImageUsesPrimaryImageOrFallsBackToLegacyColumn() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductImageService imageService = mock(ProductImageService.class);
        CategoryEntity category = new CategoryEntity();
        category.setActive(true);
        SubcategoryEntity subcategory = new SubcategoryEntity();
        subcategory.setCategory(category);
        subcategory.setActive(true);
        for (long id : new long[]{1L, 2L}) {
            ProductEntity product = new ProductEntity();
            product.setId(id);
            product.setCurrency("BRL");
            product.setPrice(new BigDecimal("10.00"));
            product.setImage("legado.jpg");
            product.setSubcategory(subcategory);
            when(productRepository.findCartDataById(id)).thenReturn(Optional.of(product));
        }
        when(imageService.findPrimaryImageUrl(1L)).thenReturn(Optional.of("https://cdn/principal.png"));
        when(imageService.findPrimaryImageUrl(2L)).thenReturn(Optional.empty());
        InternalProductController controller = new InternalProductController(
                productRepository, mock(CurrencyClientService.class), imageService, "service-secret");

        assertEquals("https://cdn/principal.png", controller.getCartData(1L, "service-secret").getBody().image());
        assertEquals("legado.jpg", controller.getCartData(2L, "service-secret").getBody().image());
    }

    @Test
    void rejectsCartDataRequestsWithoutTheServiceCredential() {
        InternalProductController controller = new InternalProductController(
                mock(ProductRepository.class),
                mock(CurrencyClientService.class),
                mock(ProductImageService.class),
                "service-secret"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCartData(17L, "wrong")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }
}
