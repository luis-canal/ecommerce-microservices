package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.InventoryMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {
}
