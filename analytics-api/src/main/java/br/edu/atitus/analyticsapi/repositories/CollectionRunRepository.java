package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.CollectionRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRunRepository extends JpaRepository<CollectionRunEntity, Long> {

    List<CollectionRunEntity> findTop20ByOrderByStartedAtDescIdDesc();
}
