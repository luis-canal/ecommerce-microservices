package br.edu.atitus.inventoryapi.repositories;

import br.edu.atitus.inventoryapi.entities.InventoryEntity;
import br.edu.atitus.inventoryapi.enums.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {

    Optional<InventoryEntity> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.productId = :productId")
    Optional<InventoryEntity> findByProductIdForUpdate(@Param("productId") Long productId);

    List<InventoryEntity> findAllByProductIdIn(Collection<Long> productIds);

    @Query("""
            select i from InventoryEntity i
            where i.quantityOnHand = i.quantityReserved
            """)
    Page<InventoryEntity> findOutOfStock(Pageable pageable);

    @Query("""
            select count(i) as inventoryCount,
                   coalesce(sum(i.quantityOnHand), 0) as quantityOnHand,
                   coalesce(sum(i.quantityReserved), 0) as quantityReserved,
                   coalesce(sum(i.quantityOnHand - i.quantityReserved), 0) as availableQuantity,
                   coalesce(sum(case when i.quantityOnHand = i.quantityReserved then 1 else 0 end), 0) as outOfStockCount,
                   coalesce(sum(case when i.quantityOnHand <= i.lowStockThreshold then 1 else 0 end), 0) as lowStockCount
            from InventoryEntity i
            """)
    InventorySummaryProjection getSummary();

    @Query("""
            select i from InventoryEntity i
            where (:productType is null or i.productType = :productType)
              and (:lowStock is null
                   or (:lowStock = true and i.quantityOnHand <= i.lowStockThreshold)
                   or (:lowStock = false and i.quantityOnHand > i.lowStockThreshold))
            """)
    Page<InventoryEntity> search(
            @Param("productType") ProductType productType,
            @Param("lowStock") Boolean lowStock,
            Pageable pageable
    );
}
