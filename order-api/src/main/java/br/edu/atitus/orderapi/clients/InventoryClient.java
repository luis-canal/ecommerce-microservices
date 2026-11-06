package br.edu.atitus.orderapi.clients;

import br.edu.atitus.orderapi.dtos.InventoryReservationRequest;
import br.edu.atitus.orderapi.dtos.InventoryReservationResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/ws/inventory/reservations")
public interface InventoryClient {

    @PostExchange
    InventoryReservationResponse create(
            @RequestBody InventoryReservationRequest request,
            @RequestHeader("X-Service-Token") String serviceToken
    );

    @GetExchange("/{reservationId}")
    InventoryReservationResponse get(
            @PathVariable Long reservationId,
            @RequestHeader("X-Service-Token") String serviceToken
    );

    @PostExchange("/{reservationId}/confirm")
    InventoryReservationResponse confirm(
            @PathVariable Long reservationId,
            @RequestHeader("X-Service-Token") String serviceToken
    );

    @PostExchange("/{reservationId}/release")
    InventoryReservationResponse release(
            @PathVariable Long reservationId,
            @RequestHeader("X-Service-Token") String serviceToken
    );
}
