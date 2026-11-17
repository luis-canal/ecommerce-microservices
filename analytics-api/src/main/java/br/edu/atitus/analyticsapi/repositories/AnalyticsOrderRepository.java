package br.edu.atitus.analyticsapi.repositories;

import br.edu.atitus.analyticsapi.entities.AnalyticsOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsOrderRepository extends JpaRepository<AnalyticsOrderEntity, Long> {
}
