package br.edu.atitus.orderapi.services;

import br.edu.atitus.orderapi.entities.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderEntity createOrder(OrderEntity order, Long userId);

    Page<OrderEntity> findOrdersByCustomerId(Long customerId, String targetCurrency, Pageable pageable);
}
