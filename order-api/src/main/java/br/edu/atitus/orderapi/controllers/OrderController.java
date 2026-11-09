package br.edu.atitus.orderapi.controllers;

import br.edu.atitus.orderapi.dtos.OrderDTO;
import br.edu.atitus.orderapi.dtos.AnalyticsOrderResponse;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.services.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/ws/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderEntity> createOrder(
            @RequestBody OrderDTO orderDTO,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Type") Integer userType,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        requireCustomer(userType);
        OrderEntity order = orderService.createOrder(orderDTO, userId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping
    public ResponseEntity<Page<OrderEntity>> listOrdersByUser(
            @RequestParam(defaultValue = "BRL") String targetCurrency,
            @PageableDefault(page = 0, size = 5, sort = {"orderDate", "id"}, direction = Direction.DESC)
            Pageable pageable,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        Page<OrderEntity> orders = orderService.findOrdersByCustomerId(userId, targetCurrency, pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderEntity> findOrderByUser(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireCustomer(userType);
        return ResponseEntity.ok(orderService.findOrderByCustomerIdAndId(userId, orderId));
    }

    @GetMapping("/internal/analytics")
    public ResponseEntity<Page<AnalyticsOrderResponse>> listOrdersForAnalytics(
            @RequestParam Instant updatedAfter,
            @PageableDefault(page = 0, size = 100, sort = {"updatedAt", "id"}, direction = Direction.ASC)
            Pageable pageable
    ) {
        Page<AnalyticsOrderResponse> response = orderService.findOrdersUpdatedAfter(updatedAfter, pageable)
                .map(AnalyticsOrderResponse::fromEntity);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderEntity> confirmOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.confirmOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderEntity> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }

    private void requireCustomer(Integer userType) {
        if (userType == null || userType != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente clientes podem acessar pedidos.");
        }
    }
}