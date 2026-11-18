package br.edu.atitus.paymentapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Mapeamento da tabela de deduplicação; a inserção é feita por SQL nativo (ON CONFLICT). */
@Entity
@Table(name = "processed_stripe_event")
public class ProcessedStripeEventEntity {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedStripeEventEntity() {
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
