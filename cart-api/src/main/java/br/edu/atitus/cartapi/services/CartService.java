package br.edu.atitus.cartapi.services;

import br.edu.atitus.cartapi.dtos.CartItemRequest;
import br.edu.atitus.cartapi.dtos.CartResponse;

public interface CartService {

    CartResponse getCart(Long customerId);

    CartResponse addItem(Long customerId, CartItemRequest request);

    CartResponse updateItemQuantity(Long customerId, Long productId, Integer quantity);

    void removeItem(Long customerId, Long productId);

    void clearCart(Long customerId);
}
