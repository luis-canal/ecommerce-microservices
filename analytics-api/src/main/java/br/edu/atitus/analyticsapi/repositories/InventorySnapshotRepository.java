package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.InventorySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshotEntity, Long> {

    Optional<InventorySnapshotEntity> findFirstByOrderByCapturedAtDesc();
}
