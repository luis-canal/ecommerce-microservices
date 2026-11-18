package br.edu.atitus.paymentapi.repositories;

import br.edu.atitus.paymentapi.entities.PaymentEntity;
import br.edu.atitus.paymentapi.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findFirstByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    Optional<PaymentEntity> findFirstByOrderIdAndCustomerIdOrderByIdDesc(Long orderId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.stripeCheckoutSessionId = :sessionId")
    Optional<PaymentEntity> lockBySessionId(@Param("sessionId") String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> lockById(@Param("id") Long id);
}
