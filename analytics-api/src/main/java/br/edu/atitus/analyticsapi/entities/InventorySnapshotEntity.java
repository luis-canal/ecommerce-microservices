package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fotografia periódica dos totais de estoque (base da evolução da disponibilidade). */
@Entity
@Table(name = "inventory_snapshot")
public class InventorySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant capturedAt;

    private long inventoryCount;
    private long quantityOnHand;
    private long quantityReserved;
    private long availableQuantity;
    private long outOfStockCount;
    private long lowStockCount;

    protected InventorySnapshotEntity() {
    }

    public InventorySnapshotEntity(
            Instant capturedAt,
            long inventoryCount,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long outOfStockCount,
            long lowStockCount
    ) {
        this.capturedAt = capturedAt;
        this.inventoryCount = inventoryCount;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = quantityReserved;
        this.availableQuantity = availableQuantity;
        this.outOfStockCount = outOfStockCount;
        this.lowStockCount = lowStockCount;
    }

    public Long getId() {
        return id;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public long getInventoryCount() {
        return inventoryCount;
    }

    public long getQuantityOnHand() {
        return quantityOnHand;
    }

    public long getQuantityReserved() {
        return quantityReserved;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public long getOutOfStockCount() {
        return outOfStockCount;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }
}
