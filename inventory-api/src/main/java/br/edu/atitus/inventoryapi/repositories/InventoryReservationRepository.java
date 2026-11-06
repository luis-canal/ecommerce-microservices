package br.edu.atitus.inventoryapi.repositories;

import br.edu.atitus.inventoryapi.entities.InventoryReservationEntity;
import br.edu.atitus.inventoryapi.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, Long> {

    Optional<InventoryReservationEntity> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservationEntity r where r.id = :reservationId")
    Optional<InventoryReservationEntity> findByIdForUpdate(@Param("reservationId") Long reservationId);

    @Query("""
            select r.id from InventoryReservationEntity r
            where r.status = :status and r.expiresAt <= :now
            order by r.expiresAt asc
            """)
    List<Long> findExpiredReservationIds(
            @Param("status") ReservationStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
