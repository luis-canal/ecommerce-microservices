package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Situação atual dos produtos sem estoque ou com estoque baixo (substituída a cada coleta). */
@Entity
@Table(name = "inventory_product_status")
public class InventoryProductStatusEntity {

    @Id
    private Long productId;

    @Column(length = 32)
    private String productType;

    private long quantityOnHand;
    private long quantityReserved;
    private long availableQuantity;
    private long lowStockThreshold;
    private boolean lowStock;
    private boolean outOfStock;
    private Instant sourceUpdatedAt;

    @Column(nullable = false)
    private Instant collectedAt;

    protected InventoryProductStatusEntity() {
    }

    public InventoryProductStatusEntity(
            Long productId,
            String productType,
            long quantityOnHand,
            long quantityReserved,
            long availableQuantity,
            long lowStockThreshold,
            boolean lowStock,
            boolean outOfStock,
            Instant sourceUpdatedAt,
            Instant collectedAt
    ) {
        this.productId = productId;
        this.productType = productType;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = quantityReserved;
        this.availableQuantity = availableQuantity;
        this.lowStockThreshold = lowStockThreshold;
        this.lowStock = lowStock;
        this.outOfStock = outOfStock;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.collectedAt = collectedAt;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductType() {
        return productType;
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

    public long getLowStockThreshold() {
        return lowStockThreshold;
    }

    public boolean isLowStock() {
        return lowStock;
    }

    public boolean isOutOfStock() {
        return outOfStock;
    }

    public void markOutOfStock() {
        this.outOfStock = true;
    }

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
