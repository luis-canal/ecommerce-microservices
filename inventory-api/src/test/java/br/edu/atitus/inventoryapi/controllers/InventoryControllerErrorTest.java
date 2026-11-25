package br.edu.atitus.inventoryapi.controllers;

import br.edu.atitus.inventoryapi.services.InventoryAuthorizationService;
import br.edu.atitus.inventoryapi.services.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InventoryControllerErrorTest {

    @Test
    void returnsNotFoundMessageAsPlainTextForMissingInventory() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getInventory(42L)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Estoque não encontrado para este produto."
        ));
        MockMvc mockMvc = standaloneSetup(new InventoryController(
                service,
                new InventoryAuthorizationService()
        )).setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(get("/ws/inventory/42").header("X-User-Type", 0))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Estoque não encontrado para este produto."));
    }
}
