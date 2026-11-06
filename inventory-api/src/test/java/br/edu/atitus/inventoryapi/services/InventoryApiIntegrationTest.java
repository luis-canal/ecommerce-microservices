package br.edu.atitus.inventoryapi.services;

import br.edu.atitus.inventoryapi.dtos.CreateReservationRequest;
import br.edu.atitus.inventoryapi.dtos.ReservationItemRequest;
import br.edu.atitus.inventoryapi.dtos.StockMovementRequest;
import br.edu.atitus.inventoryapi.entities.InventoryEntity;
import br.edu.atitus.inventoryapi.enums.MovementType;
import br.edu.atitus.inventoryapi.enums.ProductType;
import br.edu.atitus.inventoryapi.enums.ReservationStatus;
import br.edu.atitus.inventoryapi.repositories.InventoryRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryMovementRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:"
})
@Testcontainers(disabledWithoutDocker = true)
class InventoryApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory_movement, inventory_reservation_item,
                inventory_reservation, inventory RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void createsAtomicMultiItemReservationAndKeepsInventoryPhysicalQuantity() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        createInventory(102L, ProductType.MULTI_UNIT, 4);

        var reservation = reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3), new ReservationItemRequest(102L, 2))
        ));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.expiresAt()).isAfter(reservation.createdAt());
        assertThat(reservation.items()).extracting("productId").containsExactly(101L, 102L);
        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityOnHand()).isEqualTo(8);
        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityReserved()).isEqualTo(3);
        assertThat(inventoryRepository.findByProductId(102L).orElseThrow().getQuantityReserved()).isEqualTo(2);
    }

    @Test
    void existingOrderReservationIsIdempotentAndRejectsDifferentItems() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        CreateReservationRequest request = new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        );

        var created = reservationService.create(request);
        var repeated = reservationService.create(request);

        assertThat(repeated.reservationId()).isEqualTo(created.reservationId());
        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityReserved()).isEqualTo(3);
        assertThatThrownBy(() -> reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 2))
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedMultiItemReservationDoesNotPartiallyReserveAnyProduct() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        createInventory(102L, ProductType.MULTI_UNIT, 1);

        assertThatThrownBy(() -> reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3), new ReservationItemRequest(102L, 2))
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityReserved()).isZero();
        assertThat(inventoryRepository.findByProductId(102L).orElseThrow().getQuantityReserved()).isZero();
        assertThat(reservationRepository.findByOrderId(1001L)).isEmpty();
    }

    @Test
    void confirmingReservationTwiceOnlyDeductsStockAndRecordsMovementOnce() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        var created = reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        ));

        var confirmed = reservationService.confirm(created.reservationId());
        var repeated = reservationService.confirm(created.reservationId());

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(repeated.status()).isEqualTo(ReservationStatus.CONFIRMED);
        InventoryEntity inventory = inventoryRepository.findByProductId(101L).orElseThrow();
        assertThat(inventory.getQuantityOnHand()).isEqualTo(5);
        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(movementRepository.findAll()).singleElement().satisfies(movement -> {
            assertThat(movement.getMovementType()).isEqualTo(MovementType.RESERVATION_CONFIRMATION);
            assertThat(movement.getQuantityDelta()).isEqualTo(-3);
            assertThat(movement.getOrderId()).isEqualTo(1001L);
        });
    }

    @Test
    void releaseReturnsReservedUnitsWithoutChangingPhysicalStockAndIsIdempotent() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        var created = reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        ));

        var released = reservationService.release(created.reservationId());
        var repeated = reservationService.release(created.reservationId());

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(repeated.status()).isEqualTo(ReservationStatus.RELEASED);
        InventoryEntity inventory = inventoryRepository.findByProductId(101L).orElseThrow();
        assertThat(inventory.getQuantityOnHand()).isEqualTo(8);
        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void expiredReservationIsReleasedAndCannotBeConfirmed() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        var created = reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        ));
        jdbcTemplate.update(
                """
                UPDATE inventory_reservation
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '20 minutes',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                created.reservationId()
        );

        var response = reservationService.confirm(created.reservationId());

        assertThat(response.status()).isEqualTo(ReservationStatus.EXPIRED);
        InventoryEntity inventory = inventoryRepository.findByProductId(101L).orElseThrow();
        assertThat(inventory.getQuantityOnHand()).isEqualTo(8);
        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void expirationJobReleasesDueReservationsAndRecordsExpiration() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        var created = reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        ));
        jdbcTemplate.update(
                """
                UPDATE inventory_reservation
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '20 minutes',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                created.reservationId()
        );

        assertThat(reservationService.expireDueReservations()).isEqualTo(1);
        var expired = reservationService.get(created.reservationId());
        assertThat(expired.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expired.expiredAt()).isNotNull();
        InventoryEntity inventory = inventoryRepository.findByProductId(101L).orElseThrow();
        assertThat(inventory.getQuantityOnHand()).isEqualTo(8);
        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void concurrentReservationsCannotExceedAvailableQuantity() throws Exception {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> attemptConcurrentReservation(2001L, ready, start, succeeded, rejected));
            Future<?> second = executor.submit(() -> attemptConcurrentReservation(2002L, ready, start, succeeded, rejected));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        InventoryEntity inventory = inventoryRepository.findByProductId(101L).orElseThrow();
        assertThat(inventory.getQuantityReserved()).isEqualTo(5);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    void singleUnitProductCannotBeReservedMoreThanOncePerCatalogId() {
        createInventory(101L, ProductType.SINGLE_UNIT, 1);

        assertThatThrownBy(() -> reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 2))
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void manualExitCannotConsumeUnitsHeldByReservations() {
        createInventory(101L, ProductType.MULTI_UNIT, 8);
        reservationService.create(new CreateReservationRequest(
                1001L,
                List.of(new ReservationItemRequest(101L, 3))
        ));

        assertThatThrownBy(() -> inventoryService.registerManualExit(
                101L,
                new StockMovementRequest(6, "Manual removal")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityOnHand()).isEqualTo(8);
    }

    @Test
    void singleUnitPhysicalQuantityCannotBeRaisedAboveOne() {
        createInventory(101L, ProductType.SINGLE_UNIT, 1);

        assertThatThrownBy(() -> inventoryService.registerEntry(
                101L,
                new StockMovementRequest(1, "Invalid extra unit")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(inventoryRepository.findByProductId(101L).orElseThrow().getQuantityOnHand()).isEqualTo(1);
    }

    @Test
    void analyticsSummaryCountsOutOfStockAndLowStockItems() {
        createInventory(101L, ProductType.MULTI_UNIT, 0);
        createInventory(102L, ProductType.MULTI_UNIT, 5);
        jdbcTemplate.update("UPDATE inventory SET low_stock_threshold = 5 WHERE product_id = 102");

        var summary = inventoryService.getAnalyticsSummary();

        assertThat(summary.inventoryCount()).isEqualTo(2);
        assertThat(summary.quantityOnHand()).isEqualTo(5);
        assertThat(summary.quantityReserved()).isZero();
        assertThat(summary.availableQuantity()).isEqualTo(5);
        assertThat(summary.outOfStockCount()).isEqualTo(1);
        assertThat(summary.lowStockCount()).isEqualTo(2);
        assertThat(inventoryService.getOutOfStock(org.springframework.data.domain.PageRequest.of(0, 10)))
                .extracting("productId")
                .containsExactly(101L);
    }

    private void attemptConcurrentReservation(
            Long orderId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger succeeded,
            AtomicInteger rejected
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent reservations.");
            }
            reservationService.create(new CreateReservationRequest(
                    orderId,
                    List.of(new ReservationItemRequest(101L, 5))
            ));
            succeeded.incrementAndGet();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                throw exception;
            }
            rejected.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent reservation test interrupted.", exception);
        }
    }

    private void createInventory(Long productId, ProductType productType, long physicalQuantity) {
        inventoryRepository.saveAndFlush(new InventoryEntity(productId, productType));
        jdbcTemplate.update(
                "UPDATE inventory SET quantity_on_hand = ? WHERE product_id = ?",
                physicalQuantity,
                productId
        );
    }
}
