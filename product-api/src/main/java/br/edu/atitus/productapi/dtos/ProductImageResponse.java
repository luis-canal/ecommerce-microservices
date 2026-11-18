package br.edu.atitus.productapi.dtos;

public record ProductImageResponse(
        Long id,
        String url,
        boolean isPrimary,
        int position
) {
}
