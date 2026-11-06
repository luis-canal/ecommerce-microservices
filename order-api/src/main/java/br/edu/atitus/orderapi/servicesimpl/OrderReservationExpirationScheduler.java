package br.edu.atitus.orderapi.servicesimpl;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderReservationExpirationScheduler {

    private final br.edu.atitus.orderapi.services.OrderService orderService;

    public OrderReservationExpirationScheduler(br.edu.atitus.orderapi.services.OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${order.reservations.expiration-scan-ms:30000}")
    public void reconcileExpiredReservations() {
        orderService.reconcileExpiredReservations();
    }
}
