package br.edu.atitus.orderapi.dtos;

public record ProductResponse(
        Long id,
        String brand,
        String model,
        String description,
        String currency,
        double price,
        String image,

        String environment,
        String promotionMessage,
        String targetCurrency,
        double convertedPrice
) {}
