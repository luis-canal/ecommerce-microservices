package br.edu.atitus.inventoryapi.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${inventory.reservations.expiration-scan-ms:30000}")
    public void expireReservations() {
        reservationService.expireDueReservations();
    }
}
