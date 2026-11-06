package br.edu.atitus.inventoryapi.entities;

import br.edu.atitus.inventoryapi.enums.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", nullable = false)
    private InventoryEntity inventory;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovementEntity() {
    }

    public InventoryMovementEntity(
            InventoryEntity inventory,
            MovementType movementType,
            long quantityDelta,
            String reason
    ) {
        this.inventory = inventory;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
    }

    @PrePersist
    private void beforeInsert() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return inventory.getProductId();
    }

    public MovementType getMovementType() {
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
