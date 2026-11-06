package br.edu.atitus.inventoryapi.repositories;

import br.edu.atitus.inventoryapi.entities.InventoryReservationItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItemEntity, Long> {

    @Query("""
            select item from InventoryReservationItemEntity item
            where item.reservation.id = :reservationId
            order by item.inventory.productId
            """)
    List<InventoryReservationItemEntity> findAllByReservationIdOrderByProductId(
            @Param("reservationId") Long reservationId
    );
}
