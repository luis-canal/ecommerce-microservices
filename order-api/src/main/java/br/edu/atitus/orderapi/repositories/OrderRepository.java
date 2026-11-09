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

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithItemsByCustomerIdAndId(Long customerId, Long id);

    @Query(
            value = """
                    select o.id from OrderEntity o
                    where (:orderId is null or o.id = :orderId)
                      and (:status is null or o.status = :status)
                      and (:customerId is null or o.customerId = :customerId)
                      and (:createdFrom is null or o.orderDate >= :createdFrom)
                      and (:createdTo is null or o.orderDate <= :createdTo)
                      and (:confirmedFrom is null or o.confirmedAt >= :confirmedFrom)
                      and (:confirmedTo is null or o.confirmedAt <= :confirmedTo)
                    """,
            countQuery = """
                    select count(o.id) from OrderEntity o
                    where (:orderId is null or o.id = :orderId)
                      and (:status is null or o.status = :status)
                      and (:customerId is null or o.customerId = :customerId)
                      and (:createdFrom is null or o.orderDate >= :createdFrom)
                      and (:createdTo is null or o.orderDate <= :createdTo)
                      and (:confirmedFrom is null or o.confirmedAt >= :confirmedFrom)
                      and (:confirmedTo is null or o.confirmedAt <= :confirmedTo)
                    """
    )
    Page<Long> findIdsForAdmin(
            @Param("orderId") Long orderId,
            @Param("status") OrderStatus status,
            @Param("customerId") Long customerId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            @Param("confirmedFrom") Instant confirmedFrom,
            @Param("confirmedTo") Instant confirmedTo,
            Pageable pageable
    );

    @Query(
            value = "select o.id from OrderEntity o where o.updatedAt >= :updatedAfter",
            countQuery = "select count(o.id) from OrderEntity o where o.updatedAt >= :updatedAfter"
    )
    Page<Long> findIdsUpdatedAfter(@Param("updatedAfter") Instant updatedAfter, Pageable pageable);

    List<OrderEntity> findByStatusAndReservationExpiresAtBefore(
            OrderStatus status,
            Instant expiresAt
    );

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByStatusAndOrderDateBefore(OrderStatus status, Instant orderDate);
}
