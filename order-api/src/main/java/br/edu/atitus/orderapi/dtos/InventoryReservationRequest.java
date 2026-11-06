package br.edu.atitus.orderapi.dtos;

import java.util.List;

public record InventoryReservationRequest(Long orderId, List<Item> items) {
    public record Item(Long productId, long quantity) {
    }
}
