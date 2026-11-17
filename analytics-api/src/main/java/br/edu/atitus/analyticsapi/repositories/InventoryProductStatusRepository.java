package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.InventoryProductStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryProductStatusRepository extends JpaRepository<InventoryProductStatusEntity, Long> {
}
