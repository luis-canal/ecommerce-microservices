package br.edu.atitus.cartapi.servicesimpl;

import br.edu.atitus.cartapi.clients.InventoryApiClient;
import br.edu.atitus.cartapi.clients.ProductApiClient;
import br.edu.atitus.cartapi.dtos.CartItemRequest;
import br.edu.atitus.cartapi.dtos.InventoryData;
import br.edu.atitus.cartapi.dtos.ProductData;
import br.edu.atitus.cartapi.entities.CartEntity;
import br.edu.atitus.cartapi.entities.CartItemEntity;
import br.edu.atitus.cartapi.repositories.CartItemRepository;
import br.edu.atitus.cartapi.repositories.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CartServiceJpaTest {

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductApiClient productApiClient;
    private InventoryApiClient inventoryApiClient;
    private CartServiceJpa service;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        productApiClient = mock(ProductApiClient.class);
        inventoryApiClient = mock(InventoryApiClient.class);
        service = new CartServiceJpa(cartRepository, cartItemRepository, productApiClient, inventoryApiClient);

        CartEntity cart = new CartEntity(42L);
        ReflectionTestUtils.setField(cart, "id", 7L);
        when(cartRepository.findByCustomerId(42L)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerIdForUpdate(42L)).thenReturn(Optional.of(cart));
    }

    @Test
    void replacesTheQuantityWhenAddingAnExistingProduct() {
        CartEntity cart = cart();
        CartItemEntity existing = new CartItemEntity(cart, 17L, 1);
        when(cartItemRepository.findByCartIdAndProductId(7L, 17L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findAllByCartIdOrderByAddedAtAsc(7L)).thenReturn(List.of(existing));
        when(productApiClient.getProduct(17L)).thenReturn(Optional.of(product("MULTI_UNIT")));
        when(inventoryApiClient.getAvailability(17L)).thenReturn(Optional.of(inventory("MULTI_UNIT", 8)));

        var response = service.addItem(42L, new CartItemRequest(17L, 3));

        assertEquals(3, existing.getDesiredQuantity());
        assertEquals(new BigDecimal("45.00"), response.estimatedSubtotal());
        verify(cartItemRepository, never()).save(any(CartItemEntity.class));
    }

    @Test
    void rejectsSingleUnitQuantitiesOtherThanOne() {
        when(productApiClient.getProduct(17L)).thenReturn(Optional.of(product("SINGLE_UNIT")));
        when(inventoryApiClient.getAvailability(17L)).thenReturn(Optional.of(inventory("SINGLE_UNIT", 8)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.addItem(42L, new CartItemRequest(17L, 2))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(cartItemRepository, never()).save(any(CartItemEntity.class));
        verify(cartRepository, never()).saveAndFlush(any(CartEntity.class));
    }

    @Test
    void rejectsQuantitiesAboveCurrentAvailability() {
        when(productApiClient.getProduct(17L)).thenReturn(Optional.of(product("MULTI_UNIT")));
        when(inventoryApiClient.getAvailability(17L)).thenReturn(Optional.of(inventory("MULTI_UNIT", 2)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.addItem(42L, new CartItemRequest(17L, 3))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(cartItemRepository, never()).save(any(CartItemEntity.class));
    }

    @Test
    void retainsInactiveProductsAndMarksThemUnavailableInTheEstimate() {
        CartItemEntity item = new CartItemEntity(cart(), 17L, 2);
        ProductData inactiveProduct = new ProductData(
                17L, "Cuia artesanal", "Marca", "CU-01", "BRL",
                new BigDecimal("15.00"), null, "MULTI_UNIT", false, new BigDecimal("15.00")
        );
        when(cartItemRepository.findAllByCartIdOrderByAddedAtAsc(7L)).thenReturn(List.of(item));
        when(productApiClient.getProduct(17L)).thenReturn(Optional.of(inactiveProduct));
        when(inventoryApiClient.getAvailability(17L)).thenReturn(Optional.of(inventory("MULTI_UNIT", 8)));

        var response = service.getCart(42L);

        assertEquals(false, response.items().getFirst().active());
        assertEquals(true, response.items().getFirst().productAvailable());
        assertEquals(false, response.items().getFirst().available());
        assertEquals(new BigDecimal("30.00"), response.estimatedSubtotal());
        assertEquals(true, response.estimateComplete());
    }

    @Test
    void keepsRemovedProductsButMarksTheEstimateIncomplete() {
        CartItemEntity item = new CartItemEntity(cart(), 17L, 2);
        when(cartItemRepository.findAllByCartIdOrderByAddedAtAsc(7L)).thenReturn(List.of(item));
        when(productApiClient.getProduct(17L)).thenReturn(Optional.empty());

        var response = service.getCart(42L);

        assertEquals(false, response.items().getFirst().productAvailable());
        assertEquals(null, response.items().getFirst().unitPrice());
        assertEquals(null, response.estimatedSubtotal());
        assertEquals(false, response.estimateComplete());
    }

    private CartEntity cart() {
        CartEntity cart = new CartEntity(42L);
        ReflectionTestUtils.setField(cart, "id", 7L);
        return cart;
    }

    private ProductData product(String type) {
        return new ProductData(
                17L, "Cuia artesanal", "Marca", "CU-01", "BRL",
                new BigDecimal("15.00"), null, type, true, new BigDecimal("15.00")
        );
    }

    private InventoryData inventory(String type, long available) {
        return new InventoryData(17L, type, available, 0, available);
    }
}
