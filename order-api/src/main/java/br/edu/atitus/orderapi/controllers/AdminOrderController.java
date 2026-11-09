package br.edu.atitus.orderapi.controllers;

import br.edu.atitus.orderapi.dtos.AdminOrderResponse;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.services.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/ws/admin/orders")
public class AdminOrderController {

    private static final int ADMIN_USER_TYPE = 0;

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> listOrders(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) Instant confirmedFrom,
            @RequestParam(required = false) Instant confirmedTo,
            @PageableDefault(page = 0, size = 20, sort = {"orderDate", "id"}, direction = Direction.DESC)
            Pageable pageable,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireAdmin(userType);
        Page<AdminOrderResponse> response = orderService.findOrdersForAdmin(
                        orderId, status, customerId, createdFrom, createdTo, confirmedFrom, confirmedTo, pageable
                )
                .map(AdminOrderResponse::fromEntity);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderResponse> findOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireAdmin(userType);
        return ResponseEntity.ok(AdminOrderResponse.fromEntity(orderService.findOrderForAdmin(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<AdminOrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        requireAdmin(userType);
        return ResponseEntity.ok(AdminOrderResponse.fromEntity(orderService.cancelOrder(orderId)));
    }

    private void requireAdmin(Integer userType) {
        if (userType == null || userType != ADMIN_USER_TYPE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administradores podem gerenciar pedidos.");
        }
    }
}
