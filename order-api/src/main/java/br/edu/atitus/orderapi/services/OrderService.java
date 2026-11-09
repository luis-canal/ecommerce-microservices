package br.edu.atitus.orderapi.services;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.dtos.OrderDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface OrderService {
    OrderEntity createOrder(OrderDTO request, Long userId, String idempotencyKey);

    Page<OrderEntity> findOrdersByCustomerId(Long customerId, String targetCurrency, Pageable pageable);

    OrderEntity findOrderByCustomerIdAndId(Long customerId, Long orderId);

    Page<OrderEntity> findOrdersForAdmin(
            Long orderId,
            OrderStatus status,
            Long customerId,
            Instant createdFrom,
            Instant createdTo,
            Instant confirmedFrom,
            Instant confirmedTo,
            Pageable pageable
    );

    OrderEntity findOrderForAdmin(Long orderId);

    Page<OrderEntity> findOrdersUpdatedAfter(Instant updatedAfter, Pageable pageable);

    OrderEntity confirmOrder(Long orderId);

    OrderEntity cancelOrder(Long orderId);

    int reconcileExpiredReservations();
}
