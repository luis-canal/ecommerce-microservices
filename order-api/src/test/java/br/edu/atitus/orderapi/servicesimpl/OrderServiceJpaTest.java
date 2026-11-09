package br.edu.atitus.orderapi.servicesimpl;

import br.edu.atitus.orderapi.clients.CurrencyClient;
import br.edu.atitus.orderapi.clients.InventoryClient;
import br.edu.atitus.orderapi.clients.ProductClient;
import br.edu.atitus.orderapi.dtos.InventoryReservationRequest;
import br.edu.atitus.orderapi.dtos.InventoryReservationResponse;
import br.edu.atitus.orderapi.dtos.OrderDTO;
import br.edu.atitus.orderapi.dtos.OrderItemDTO;
import br.edu.atitus.orderapi.dtos.ProductResponse;
import br.edu.atitus.orderapi.dtos.ShippingAddressDTO;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderItemEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.repositories.OrderRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceJpaTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductClient productClient;
    @Mock
    private CurrencyClient currencyClient;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private OrderPersistenceService persistenceService;

    private OrderServiceJpa orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceJpa(
                orderRepository,
                productClient,
                currencyClient,
                inventoryClient,
                persistenceService,
                new RegionalShippingCostCalculator(),
                "service-token"
        );
    }

    @Test
    void createsPendingOrderOnlyAfterActiveInventoryReservation() {
        AtomicReference<OrderEntity> persistedOrder = new AtomicReference<>();
        when(productClient.getProductById(17L, "BRL")).thenReturn(product());
        when(persistenceService.savePending(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            order.setId(91L);
            persistedOrder.set(order);
            return order;
        });
        when(inventoryClient.create(any(InventoryReservationRequest.class), eq("service-token")))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "ACTIVE", Instant.now().plusSeconds(600)
                ));
        when(persistenceService.attachReservation(
                eq(91L), eq(301L), any(Instant.class), eq(OrderStatus.PENDING_PAYMENT)
        )).thenAnswer(invocation -> {
            OrderEntity order = persistedOrder.get();
            order.setReservationId(301L);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            return order;
        });

        OrderEntity created = orderService.createOrder(
                new OrderDTO(List.of(new OrderItemDTO(17L, 2)), shippingAddress()),
                42L,
                "retry-abc"
        );

        ArgumentCaptor<InventoryReservationRequest> requestCaptor =
                ArgumentCaptor.forClass(InventoryReservationRequest.class);
        verify(inventoryClient).create(requestCaptor.capture(), eq("service-token"));
        assertEquals(91L, requestCaptor.getValue().orderId());
        assertEquals(List.of(new InventoryReservationRequest.Item(17L, 2)), requestCaptor.getValue().items());
        assertEquals(OrderStatus.PENDING_PAYMENT, created.getStatus());
        assertEquals(new BigDecimal("25.50"), created.getSubtotal());
        assertEquals(new BigDecimal("20.00"), created.getShippingAmount());
        assertEquals(new BigDecimal("45.50"), created.getTotalAmount());
        assertEquals("RS", created.getShippingAddress().getState());
        assertEquals("Widget", created.getItems().getFirst().getProductName());
        assertEquals("BRL", created.getItems().getFirst().getCurrencyAtPurchase());
        assertEquals(new BigDecimal("12.75"), created.getItems().getFirst().getUnitPrice());
        assertEquals(17L, created.getItems().getFirst().getCategoryId());
        assertEquals("Category", created.getItems().getFirst().getCategoryName());
        assertEquals(new BigDecimal("25.50"), created.getItems().getFirst().getSubtotal());

        when(orderRepository.findByCustomerIdAndIdempotencyKey(42L, "retry-abc"))
                .thenReturn(Optional.of(created));
        ResponseStatusException changedAddress = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        new OrderDTO(
                                List.of(new OrderItemDTO(17L, 2)),
                                new ShippingAddressDTO(
                                        "99000-000", "Rua das Flores", "150", null,
                                        "Centro", "Erechim", "RS"
                                )
                        ),
                        42L,
                        "retry-abc"
                )
        );
        assertEquals(HttpStatus.CONFLICT, changedAddress.getStatusCode());

        OrderEntity retry = orderService.createOrder(
                new OrderDTO(List.of(new OrderItemDTO(17L, 2)), shippingAddress()),
                42L,
                "retry-abc"
        );
        assertEquals(created.getId(), retry.getId());
        verify(inventoryClient, times(1)).create(any(InventoryReservationRequest.class), eq("service-token"));
    }

    @Test
    void rejectsEmptyOrderBeforeCallingOtherServices() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(new OrderDTO(List.of(), shippingAddress()), 42L, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(productClient, never()).getProductById(any(), any());
        verify(inventoryClient, never()).create(any(), any());
    }

    @Test
    void rejectsMissingAddressBeforeCallingOtherServices() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        new OrderDTO(List.of(new OrderItemDTO(17L, 1)), null),
                        42L,
                        null
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(productClient, never()).getProductById(any(), any());
        verify(inventoryClient, never()).create(any(), any());
    }

    @Test
    void deletesProvisionalOrderWhenInventoryRejectsReservation() {
        when(productClient.getProductById(17L, "BRL")).thenReturn(product());
        when(persistenceService.savePending(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            order.setId(91L);
            return order;
        });
        when(inventoryClient.create(any(InventoryReservationRequest.class), eq("service-token")))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT,
                        "Conflict",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        new OrderDTO(List.of(new OrderItemDTO(17L, 2)), shippingAddress()),
                        42L,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(persistenceService).deleteRejectedOrder(91L);
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentRequest() {
        OrderEntity existing = new OrderEntity();
        existing.setRequestFingerprint("different-request");
        existing.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByCustomerIdAndIdempotencyKey(42L, "same-key"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        new OrderDTO(List.of(new OrderItemDTO(17L, 2)), shippingAddress()),
                        42L,
                        "same-key"
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productClient, never()).getProductById(any(), any());
        verify(inventoryClient, never()).create(any(), any());
    }

    @Test
    void retriesPendingReservationToRecoverLostInventoryResponse() {
        OrderEntity pending = new OrderEntity();
        pending.setId(91L);
        pending.setStatus(OrderStatus.RESERVATION_PENDING);
        OrderItemEntity item = new OrderItemEntity();
        item.setProductId(17L);
        item.setQuantity(2);
        item.setOrder(pending);
        pending.setItems(List.of(item));
        when(orderRepository.findByStatusAndOrderDateBefore(
                eq(OrderStatus.RESERVATION_PENDING),
                any(Instant.class)
        )).thenReturn(List.of(pending));
        when(inventoryClient.create(any(InventoryReservationRequest.class), eq("service-token")))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "ACTIVE", Instant.now().plusSeconds(600)
                ));
        when(persistenceService.attachReservation(
                eq(91L), eq(301L), any(Instant.class), eq(OrderStatus.PENDING_PAYMENT)
        )).thenAnswer(invocation -> {
            pending.setReservationId(301L);
            pending.setStatus(OrderStatus.PENDING_PAYMENT);
            return pending;
        });

        int recovered = orderService.reconcileExpiredReservations();

        assertEquals(1, recovered);
        assertEquals(OrderStatus.PENDING_PAYMENT, pending.getStatus());
        verify(inventoryClient).create(
                new InventoryReservationRequest(91L, List.of(new InventoryReservationRequest.Item(17L, 2))),
                "service-token"
        );
    }

    @Test
    void paginatesOrderIdsBeforeLoadingSnapshotItems() {
        var pageable = PageRequest.of(0, 5);
        OrderEntity second = orderWithSnapshot(2L);
        OrderEntity first = orderWithSnapshot(1L);
        when(orderRepository.findIdsByCustomerId(42L, pageable))
                .thenReturn(new PageImpl<>(List.of(2L, 1L), pageable, 2));
        when(orderRepository.findAllWithItemsByIdIn(List.of(2L, 1L)))
                .thenReturn(List.of(first, second));

        var page = orderService.findOrdersByCustomerId(42L, "BRL", pageable);

        assertEquals(List.of(2L, 1L), page.getContent().stream().map(OrderEntity::getId).toList());
        assertEquals(2, page.getTotalElements());
        verify(productClient, never()).getProductById(any(), any());
        verify(currencyClient, never()).getCurrency(any(), any());
    }

    @Test
    void customerDetailReadsOnlyTheirOrderAndUsesPersistedProductSnapshot() {
        OrderEntity order = orderWithSnapshot(73L);
        order.setSubtotal(new BigDecimal("12.75"));
        order.setTotalAmount(new BigDecimal("32.75"));
        when(orderRepository.findWithItemsByCustomerIdAndId(42L, 73L)).thenReturn(Optional.of(order));

        OrderEntity result = orderService.findOrderByCustomerIdAndId(42L, 73L);

        assertEquals(73L, result.getId());
        assertEquals(new BigDecimal("12.75"), result.getTotalPrice());
        assertEquals("Widget", result.getItems().getFirst().getProduct().name());
        verify(productClient, never()).getProductById(any(), any());
        verify(currencyClient, never()).getCurrency(any(), any());
    }

    @Test
    void adminListUsesRequestedFiltersAndReturnsPersistedSnapshots() {
        var pageable = PageRequest.of(0, 20);
        OrderEntity order = orderWithSnapshot(73L);
        when(orderRepository.findIdsForAdmin(
                eq(73L),
                eq(OrderStatus.PENDING_PAYMENT),
                eq(42L),
                any(Instant.class),
                any(Instant.class),
                any(Instant.class),
                any(Instant.class),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(73L), pageable, 1));
        when(orderRepository.findAllWithItemsByIdIn(List.of(73L))).thenReturn(List.of(order));

        var page = orderService.findOrdersForAdmin(
                73L,
                OrderStatus.PENDING_PAYMENT,
                42L,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                pageable
        );

        assertEquals(List.of(73L), page.getContent().stream().map(OrderEntity::getId).toList());
        assertEquals("Category", page.getContent().getFirst().getItems().getFirst().getProduct().categoryName());
        verify(productClient, never()).getProductById(any(), any());
    }

    @Test
    void marksOrderExpiredWhenInventoryRejectsConfirmationAsExpired() {
        OrderEntity pending = new OrderEntity();
        pending.setId(91L);
        pending.setStatus(OrderStatus.PENDING_PAYMENT);
        pending.setReservationId(301L);
        pending.setItems(List.of());
        when(orderRepository.findWithItemsById(91L)).thenReturn(Optional.of(pending));
        when(inventoryClient.confirm(301L, "service-token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT,
                        "Expired",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));
        when(inventoryClient.get(301L, "service-token"))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "EXPIRED", Instant.now().minusSeconds(1)
                ));
        when(persistenceService.updateStatus(91L, OrderStatus.EXPIRED)).thenAnswer(invocation -> {
            pending.setStatus(OrderStatus.EXPIRED);
            return pending;
        });

        OrderEntity result = orderService.confirmOrder(91L);

        assertEquals(OrderStatus.EXPIRED, result.getStatus());
        verify(inventoryClient).confirm(301L, "service-token");
        verify(inventoryClient).get(301L, "service-token");
    }

    @Test
    void reconcilesConfirmationWhenInventoryAlreadyConfirmedTheReservation() {
        OrderEntity pending = new OrderEntity();
        pending.setId(91L);
        pending.setStatus(OrderStatus.PENDING_PAYMENT);
        pending.setReservationId(301L);
        pending.setItems(List.of());
        when(orderRepository.findWithItemsById(91L)).thenReturn(Optional.of(pending));
        when(inventoryClient.confirm(301L, "service-token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT,
                        "Already confirmed",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));
        when(inventoryClient.get(301L, "service-token"))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "CONFIRMED", Instant.now().minusSeconds(1)
                ));
        when(persistenceService.updateStatus(91L, OrderStatus.CONFIRMED)).thenAnswer(invocation -> {
            pending.setStatus(OrderStatus.CONFIRMED);
            return pending;
        });

        OrderEntity result = orderService.confirmOrder(91L);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(inventoryClient).confirm(301L, "service-token");
        verify(inventoryClient).get(301L, "service-token");
    }

    @Test
    void cancellingPendingOrderReleasesItsReservation() {
        OrderEntity pending = new OrderEntity();
        pending.setId(91L);
        pending.setStatus(OrderStatus.PENDING_PAYMENT);
        pending.setReservationId(301L);
        pending.setItems(List.of());
        when(orderRepository.findWithItemsById(91L)).thenReturn(Optional.of(pending));
        when(inventoryClient.release(301L, "service-token"))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "RELEASED", Instant.now().plusSeconds(100)
                ));
        when(persistenceService.updateStatus(91L, OrderStatus.CANCELLED)).thenAnswer(invocation -> {
            pending.setStatus(OrderStatus.CANCELLED);
            return pending;
        });

        OrderEntity result = orderService.cancelOrder(91L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryClient).release(301L, "service-token");
    }

    @Test
    void reconcilesCancellationWhenInventoryAlreadyReleasedTheReservation() {
        OrderEntity pending = new OrderEntity();
        pending.setId(91L);
        pending.setStatus(OrderStatus.PENDING_PAYMENT);
        pending.setReservationId(301L);
        pending.setItems(List.of());
        when(orderRepository.findWithItemsById(91L)).thenReturn(Optional.of(pending));
        when(inventoryClient.release(301L, "service-token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT,
                        "Already released",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));
        when(inventoryClient.get(301L, "service-token"))
                .thenReturn(new InventoryReservationResponse(
                        301L, 91L, "RELEASED", Instant.now().plusSeconds(100)
                ));
        when(persistenceService.updateStatus(91L, OrderStatus.CANCELLED)).thenAnswer(invocation -> {
            pending.setStatus(OrderStatus.CANCELLED);
            return pending;
        });

        OrderEntity result = orderService.cancelOrder(91L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryClient).release(301L, "service-token");
        verify(inventoryClient).get(301L, "service-token");
    }

    @Test
    void confirmedOrderCannotBeCancelled() {
        OrderEntity confirmed = new OrderEntity();
        confirmed.setId(91L);
        confirmed.setStatus(OrderStatus.CONFIRMED);
        confirmed.setReservationId(301L);
        when(orderRepository.findWithItemsById(91L)).thenReturn(Optional.of(confirmed));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(91L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryClient, never()).release(any(), any());
    }

    private OrderEntity orderWithSnapshot(Long id) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        OrderItemEntity item = new OrderItemEntity();
        item.setProductId(17L);
        item.setQuantity(1);
        item.setPriceAtPurchase(new BigDecimal("12.75"));
        item.setCurrencyAtPurchase("BRL");
        item.setProductName("Widget");
        item.setCategoryId(17L);
        item.setCategoryName("Category");
        item.setSubtotal(new BigDecimal("12.75"));
        item.setOrder(order);
        order.setItems(List.of(item));
        order.setSubtotal(new BigDecimal("12.75"));
        order.setTotalAmount(new BigDecimal("12.75"));
        return order;
    }

    private ProductResponse product() {
        return new ProductResponse(
                17L,
                "Widget",
                "Brand",
                "Model",
                "Description",
                "USD",
                new BigDecimal("10.00"),
                null,
                7L,
                "Subcategory",
                17L,
                "Category",
                "MULTI_UNIT",
                true,
                null,
                null,
                "BRL",
                new BigDecimal("12.75")
        );
    }

    private ShippingAddressDTO shippingAddress() {
        return new ShippingAddressDTO(
                "99000-000",
                "Rua das Flores",
                "150",
                "Apartamento 202",
                "Centro",
                "Passo Fundo",
                "RS"
        );
    }
}
