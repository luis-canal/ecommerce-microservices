package br.edu.atitus.paymentapi.repositories;

import br.edu.atitus.paymentapi.entities.ProcessedStripeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEventEntity, String> {

    /**
     * Registra o evento na transação corrente. Retorna 0 quando ele já foi processado.
     * Um reenvio concorrente espera o commit da primeira transação e então também recebe 0.
     */
    @Modifying
    @Query(value = """
            insert into processed_stripe_event (event_id, type, processed_at)
            values (:eventId, :type, now())
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("type") String type);
}
