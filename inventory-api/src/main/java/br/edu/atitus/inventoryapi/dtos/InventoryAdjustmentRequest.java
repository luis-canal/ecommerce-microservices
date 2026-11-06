package br.edu.atitus.inventoryapi.dtos;

public record InventoryAdjustmentRequest(long newQuantity, String reason) {
}
