package br.edu.atitus.cartapi.repositories;

import br.edu.atitus.cartapi.entities.CartEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cart from CartEntity cart where cart.customerId = :customerId")
    Optional<CartEntity> findByCustomerIdForUpdate(@Param("customerId") Long customerId);

    @Modifying
    @Query(value = """
            INSERT INTO carts (customer_id, created_at, updated_at, version)
            VALUES (:customerId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (customer_id) DO NOTHING
            """, nativeQuery = true)
    int createIfMissing(@Param("customerId") Long customerId);
}
