package br.edu.atitus.orderapi.servicesimpl;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.repositories.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    public OrderPersistenceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderEntity savePending(OrderEntity order) {
        order.setStatus(OrderStatus.RESERVATION_PENDING);
        order.setOrderDate(Instant.now());
        order.setUpdatedAt(order.getOrderDate());
        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public OrderEntity attachReservation(
            Long orderId,
            Long reservationId,
            Instant expiresAt,
            OrderStatus status
    ) {
        OrderEntity order = orderRepository.findWithItemsById(orderId).orElseThrow();
        order.setReservationId(reservationId);
        order.setReservationExpiresAt(expiresAt);
        order.setStatus(status);
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order);
    }

    @Transactional
    public void deleteRejectedOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }

    @Transactional
    public OrderEntity updateStatus(Long orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findWithItemsById(orderId).orElseThrow();
        order.setStatus(status);
        order.setUpdatedAt(Instant.now());
        if (status == OrderStatus.CONFIRMED) {
            order.setConfirmedAt(Instant.now());
        } else if (status == OrderStatus.CANCELLED) {
            order.setCancelledAt(Instant.now());
        }
        return orderRepository.save(order);
    }
}
