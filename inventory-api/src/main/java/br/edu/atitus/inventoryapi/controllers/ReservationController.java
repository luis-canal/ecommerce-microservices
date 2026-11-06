package br.edu.atitus.inventoryapi.controllers;

import br.edu.atitus.inventoryapi.dtos.CreateReservationRequest;
import br.edu.atitus.inventoryapi.dtos.ReservationResponse;
import br.edu.atitus.inventoryapi.enums.ReservationStatus;
import br.edu.atitus.inventoryapi.services.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ws/inventory/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(request));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> get(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.get(reservationId));
    }

    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirm(@PathVariable Long reservationId) {
        ReservationResponse response = reservationService.confirm(reservationId);
        if (response.status() == ReservationStatus.EXPIRED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<ReservationResponse> release(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.release(reservationId));
    }
}
