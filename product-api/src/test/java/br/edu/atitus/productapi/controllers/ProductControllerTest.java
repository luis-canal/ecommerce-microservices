package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.enums.ProductStatusFilter;
import br.edu.atitus.productapi.services.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProductControllerTest {

    private ProductService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(ProductService.class);
        when(service.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc = standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void forwardsCombinedFiltersAndDefaultsTargetCurrencyToBrl() throws Exception {
        var controller = new ProductController(service);
        var pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "price"));

        controller.getAll(
                null,
                "CuIa",
                12L,
                34L,
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                pageable
        );

        verify(service).findAll(
                eq(new ProductSearchCriteria(
                        "CuIa", 12L, 34L, new BigDecimal("50.00"), new BigDecimal("100.00")
                )),
                eq(pageable),
                eq("BRL")
        );
    }

    @Test
    void publicListingIgnoresStatusParameterAndKeepsOnlyActiveProducts() throws Exception {
        when(service.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        for (String value : List.of("INACTIVE", "ALL", "FOO")) {
            mockMvc.perform(get("/products").param("status", value).header("X-User-Type", 0))
                    .andExpect(status().isOk());
        }

        verify(service, times(3)).findAll(
                eq(new ProductSearchCriteria(null, null, null, null, null, ProductStatusFilter.ACTIVE)),
                eq(PageRequest.of(0, 10, Sort.by("description"))),
                eq("BRL")
        );
    }

    @Test
    void publicDetailKeepsUsingTheActiveOnlyLookup() throws Exception {
        when(service.findById(1L, "BRL"))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Produto não encontrado"));

        mockMvc.perform(get("/products/1").param("targetCurrency", "BRL").param("status", "ALL")
                        .header("X-User-Type", 0))
                .andExpect(status().isNotFound());

        verify(service).findById(1L, "BRL");
        verify(service, never()).findByIdForAdmin(any(), any());
    }

    @Test
    void returnsBadRequestForMalformedPriceParameter() throws Exception {
        mockMvc.perform(get("/products").param("minPrice", "not-a-price"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_PLAIN))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Parâmetro inválido: minPrice."));
    }

    @Test
    void preservesSpecificValidationMessageFromService() throws Exception {
        when(service.findAll(any(), any(), any())).thenThrow(
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "O preço deve ser maior que zero."
                )
        );

        mockMvc.perform(get("/products").param("minPrice", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("O preço deve ser maior que zero."));
    }
}
