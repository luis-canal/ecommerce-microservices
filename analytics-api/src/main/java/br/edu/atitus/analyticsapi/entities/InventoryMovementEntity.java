package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Cópia das movimentações de estoque (chave = id da movimentação no inventory-api). */
@Entity
@Table(name = "inventory_movement")
public class InventoryMovementEntity {

    @Id
    private Long movementId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 32)
    private String movementType;

    private long quantityDelta;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private Long orderId;

    private Long reservationId;

    @Column(nullable = false)
    private Instant createdAt;

    protected InventoryMovementEntity() {
    }

    public InventoryMovementEntity(
            Long movementId,
            Long productId,
            String movementType,
            long quantityDelta,
            String reason,
            Long orderId,
            Long reservationId,
            Instant createdAt
    ) {
        this.movementId = movementId;
        this.productId = productId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.createdAt = createdAt;
    }

    public Long getMovementId() {
        return movementId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getMovementType() {
        return movementType;
    }

    public long getQuantityDelta() {
        return quantityDelta;
    }

    public String getReason() {
        return reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
