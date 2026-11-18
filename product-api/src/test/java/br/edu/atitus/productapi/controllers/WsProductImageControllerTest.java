package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductImageResponse;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.ProductImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WsProductImageControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ProductImageService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductImageService.class);
        mockMvc = standaloneSetup(new WsProductImageController(service, new AuthorizationService())).build();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG);
    }

    @Test
    void nonAdministratorsAreForbiddenOnEveryEndpoint() throws Exception {
        mockMvc.perform(multipart("/ws/products/1/images").file(png()).header("X-User-Type", 1))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/ws/products/1/images").header("X-User-Type", 1))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/ws/products/1/images/5/primary").header("X-User-Type", 1))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/ws/products/1/images/5").header("X-User-Type", 1))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void administratorUploadsImage() throws Exception {
        when(service.upload(eq(1L), any()))
                .thenReturn(new ProductImageResponse(5L, "https://cdn/products/1/x.png", true, 1));

        mockMvc.perform(multipart("/ws/products/1/images").file(png()).header("X-User-Type", 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.url").value("https://cdn/products/1/x.png"))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        var notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        when(service.list(99L)).thenThrow(notFound);
        when(service.upload(eq(99L), any())).thenThrow(notFound);

        mockMvc.perform(get("/ws/products/99/images").header("X-User-Type", 0))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/ws/products/99/images").file(png()).header("X-User-Type", 0))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsKeepTheirStatus() throws Exception {
        when(service.upload(eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "grande"));

        mockMvc.perform(multipart("/ws/products/1/images").file(png()).header("X-User-Type", 0))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void administratorListsSetsPrimaryAndDeletes() throws Exception {
        when(service.list(1L)).thenReturn(List.of(new ProductImageResponse(5L, "u", true, 1)));
        when(service.setPrimary(1L, 6L)).thenReturn(new ProductImageResponse(6L, "v", true, 2));

        mockMvc.perform(get("/ws/products/1/images").header("X-User-Type", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isPrimary").value(true));
        mockMvc.perform(patch("/ws/products/1/images/6/primary").header("X-User-Type", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6));
        mockMvc.perform(delete("/ws/products/1/images/6").header("X-User-Type", 0))
                .andExpect(status().isNoContent());

        verify(service).delete(1L, 6L);
    }

    @Test
    void deletingUnknownImageReturns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada."))
                .when(service).delete(1L, 77L);

        mockMvc.perform(delete("/ws/products/1/images/77").header("X-User-Type", 0))
                .andExpect(status().isNotFound());
    }
}
