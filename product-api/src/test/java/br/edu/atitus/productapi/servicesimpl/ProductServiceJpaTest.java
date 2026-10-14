package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CurrencyResponse;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceJpaTest {

    private ProductRepository repository;
    private CurrencyClientService currencyClientService;
    private ProductServiceJpa productService;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        currencyClientService = mock(CurrencyClientService.class);
        productService = new ProductServiceJpa(repository, currencyClientService);
        ReflectionTestUtils.setField(productService, "serverPort", "8000");
        ReflectionTestUtils.setField(productService, "promotionMessage", "Test promotion");
        when(repository.findById(1L)).thenReturn(Optional.of(
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
}
