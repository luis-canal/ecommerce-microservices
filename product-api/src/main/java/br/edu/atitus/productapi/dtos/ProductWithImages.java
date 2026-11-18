package br.edu.atitus.productapi.dtos;

import br.edu.atitus.productapi.entities.ProductEntity;

import java.util.List;

/**
 * Produto carregado com suas imagens. image já vem resolvido:
 * URL da principal, valor legado da coluna image ou null.
 */
public record ProductWithImages(
        ProductEntity product,
        String image,
        List<ProductImageResponse> images
) {
}
