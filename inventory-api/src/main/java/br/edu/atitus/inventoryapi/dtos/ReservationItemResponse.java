package br.edu.atitus.inventoryapi.dtos;

import br.edu.atitus.inventoryapi.entities.InventoryReservationItemEntity;

public record ReservationItemResponse(Long productId, long quantity) {

    public static ReservationItemResponse from(InventoryReservationItemEntity item) {
        return new ReservationItemResponse(item.getProductId(), item.getQuantity());
    }
}
