package br.edu.atitus.inventoryapi.repositories;

import br.edu.atitus.inventoryapi.entities.InventoryMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryMovementRepository extends
        JpaRepository<InventoryMovementEntity, Long>,
        JpaSpecificationExecutor<InventoryMovementEntity> {
}
