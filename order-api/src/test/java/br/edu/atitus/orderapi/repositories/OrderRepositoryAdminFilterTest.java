package br.edu.atitus.orderapi.repositories;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderRepositoryAdminFilterTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant BASE = Instant.parse("2026-05-10T12:00:00Z");
    private static final PageRequest PAGE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "orderDate", "id"));

    @Autowired
    private OrderRepository orderRepository;

    private OrderEntity older;
    private OrderEntity newer;

    @BeforeEach
    void seed() {
        orderRepository.deleteAll();
        older = orderRepository.save(order(10L, OrderStatus.CONFIRMED, BASE, BASE.plus(1, ChronoUnit.HOURS)));
        newer = orderRepository.save(order(20L, OrderStatus.PENDING_PAYMENT, BASE.plus(2, ChronoUnit.DAYS), null));
    }

    @Test
    void listsWithoutAnyFilter() {
        Page<Long> page = find(null, null, null, null, null, null, null);

        assertEquals(List.of(newer.getId(), older.getId()), page.getContent());
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void filtersByOrderId() {
        assertEquals(List.of(older.getId()), find(older.getId(), null, null, null, null, null, null).getContent());
    }

    @Test
    void filtersByStatus() {
        assertEquals(List.of(newer.getId()),
                find(null, OrderStatus.PENDING_PAYMENT, null, null, null, null, null).getContent());
    }

    @Test
    void filtersByCustomerId() {
        assertEquals(List.of(older.getId()), find(null, null, 10L, null, null, null, null).getContent());
    }

    @Test
    void filtersByCreatedFrom() {
        assertEquals(List.of(newer.getId()),
                find(null, null, null, BASE.plus(1, ChronoUnit.DAYS), null, null, null).getContent());
    }

    @Test
    void filtersByCreatedTo() {
        assertEquals(List.of(older.getId()),
                find(null, null, null, null, BASE.plus(1, ChronoUnit.DAYS), null, null).getContent());
    }

    @Test
    void filtersByConfirmedFrom() {
        assertEquals(List.of(older.getId()), find(null, null, null, null, null, BASE, null).getContent());
    }

    @Test
    void filtersByConfirmedTo() {
        assertEquals(List.of(older.getId()),
                find(null, null, null, null, null, null, BASE.plus(1, ChronoUnit.DAYS)).getContent());
    }

    @Test
    void countQueryHonoursFiltersAcrossPages() {
        Page<Long> page = orderRepository.findIdsForAdmin(
                null, null, null, null, null, null, null, PageRequest.of(0, 1, PAGE.getSort())
        );

        assertEquals(List.of(newer.getId()), page.getContent());
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    private Page<Long> find(
            Long orderId,
            OrderStatus status,
            Long customerId,
            Instant createdFrom,
            Instant createdTo,
            Instant confirmedFrom,
            Instant confirmedTo
    ) {
        return orderRepository.findIdsForAdmin(
                orderId, status, customerId, createdFrom, createdTo, confirmedFrom, confirmedTo, PAGE
        );
    }

    private OrderEntity order(Long customerId, OrderStatus status, Instant orderDate, Instant confirmedAt) {
        OrderEntity order = new OrderEntity();
        order.setCustomerId(customerId);
        order.setStatus(status);
        order.setOrderDate(orderDate);
        order.setConfirmedAt(confirmedAt);
        return order;
    }
}
