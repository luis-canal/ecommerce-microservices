package br.edu.atitus.cartapi.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(
        @NotNull(message = "O identificador do produto é obrigatório.")
        @Positive(message = "O identificador do produto deve ser maior que zero.")
        Long productId,
        @NotNull(message = "A quantidade é obrigatória.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        Integer quantity
) {
}
