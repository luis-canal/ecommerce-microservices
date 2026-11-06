package br.edu.atitus.orderapi.services;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.dtos.OrderDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderEntity createOrder(OrderDTO request, Long userId, String idempotencyKey);

    Page<OrderEntity> findOrdersByCustomerId(Long customerId, String targetCurrency, Pageable pageable);

    OrderEntity confirmOrder(Long orderId);

    OrderEntity cancelOrder(Long orderId);

    int reconcileExpiredReservations();
}
