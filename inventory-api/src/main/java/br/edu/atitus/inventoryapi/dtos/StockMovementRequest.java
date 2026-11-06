package br.edu.atitus.inventoryapi.dtos;

public record StockMovementRequest(long quantity, String reason) {
}
