package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.services.ProductService;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WsProductControllerTest {

    private ProductService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        mockMvc = standaloneSetup(new WsProductController(service, new AuthorizationService())).build();
    }

    @Test
    void productUpdateRequiresAnAdministrator() throws Exception {
        mockMvc.perform(put("/ws/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Produto\",\"brand\":\"Marca\",\"model\":\"Modelo\","
                                + "\"description\":\"Descrição\",\"currency\":\"BRL\",\"price\":10,"
                                + "\"subcategoryId\":2}")
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(service, never()).update(eq(1L), any());
    }

    @Test
    void productStatusUpdateRequiresAnAdministrator() throws Exception {
        mockMvc.perform(patch("/ws/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(service, never()).updateStatus(eq(1L), any());
    }

    @Test
    void administratorCanUpdateProductAndStatus() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(new ProductEntity());
        when(service.updateStatus(eq(1L), any())).thenReturn(new ProductEntity());

        mockMvc.perform(put("/ws/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Produto\",\"brand\":\"Marca\",\"model\":\"Modelo\","
                                + "\"description\":\"Descrição\",\"currency\":\"BRL\",\"price\":10,"
                                + "\"subcategoryId\":2}")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/ws/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), any());
        verify(service).updateStatus(eq(1L), any());
    }
}
