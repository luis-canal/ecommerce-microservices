package br.edu.atitus.orderapi.repositories;

import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    @Query(
            value = "select o.id from OrderEntity o where o.customerId = :customerId",
            countQuery = "select count(o.id) from OrderEntity o where o.customerId = :customerId"
    )
    Page<Long> findIdsByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @Query("select distinct o from OrderEntity o left join fetch o.items where o.id in :ids")
    List<OrderEntity> findAllWithItemsByIdIn(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithItemsById(Long id);

    List<OrderEntity> findByStatusAndReservationExpiresAtBefore(
            OrderStatus status,
            Instant expiresAt
    );

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByStatusAndOrderDateBefore(OrderStatus status, Instant orderDate);
}
