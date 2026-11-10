package br.edu.atitus.cartapi.controllers;

import br.edu.atitus.cartapi.services.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CartControllerTest {

    @Test
    void rejectsNonCustomerUsers() {
        CartService service = mock(CartService.class);
        CartController controller = new CartController(service);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCart(42L, 0)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void patchAcceptsOnlyTheNewQuantityInItsBody() throws Exception {
        CartService service = mock(CartService.class);
        MockMvc mockMvc = standaloneSetup(new CartController(service)).build();
        when(service.updateItemQuantity(42L, 17L, 3)).thenReturn(null);

        mockMvc.perform(patch("/ws/cart/items/17")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}")
                        .header("X-User-Id", 42L)
                        .header("X-User-Type", 1))
                .andExpect(status().isOk());

        verify(service).updateItemQuantity(eq(42L), eq(17L), eq(3));
    }
}
