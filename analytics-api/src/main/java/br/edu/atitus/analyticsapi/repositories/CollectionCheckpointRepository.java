package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionCheckpointRepository extends JpaRepository<CollectionCheckpointEntity, String> {
}
