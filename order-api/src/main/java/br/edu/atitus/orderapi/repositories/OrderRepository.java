package br.edu.atitus.orderapi.repositories;

import br.edu.atitus.orderapi.entities.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByCustomerId(Long customerId, Pageable pageable);
}
