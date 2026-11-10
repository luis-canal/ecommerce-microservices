package br.edu.atitus.cartapi.controllers;

import br.edu.atitus.cartapi.dtos.CartItemRequest;
import br.edu.atitus.cartapi.dtos.CartQuantityRequest;
import br.edu.atitus.cartapi.dtos.CartResponse;
import br.edu.atitus.cartapi.services.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ws/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @RequestHeader("X-User-Id") Long customerId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody CartItemRequest request,
            @RequestHeader("X-User-Id") Long customerId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(cartService.addItem(customerId, request));
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable Long productId,
            @Valid @RequestBody CartQuantityRequest request,
            @RequestHeader("X-User-Id") Long customerId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(cartService.updateItemQuantity(customerId, productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long productId,
            @RequestHeader("X-User-Id") Long customerId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        cartService.removeItem(customerId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clearCart(
            @RequestHeader("X-User-Id") Long customerId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }

    private void requireCustomer(Integer userType) {
        if (userType == null || userType != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente clientes podem acessar o carrinho.");
        }
    }
}
