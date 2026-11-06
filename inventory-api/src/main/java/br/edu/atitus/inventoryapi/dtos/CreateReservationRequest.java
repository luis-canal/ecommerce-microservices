package br.edu.atitus.inventoryapi.dtos;

import java.util.List;

public record CreateReservationRequest(Long orderId, List<ReservationItemRequest> items) {
}
