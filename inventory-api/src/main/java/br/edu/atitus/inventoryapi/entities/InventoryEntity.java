package br.edu.atitus.inventoryapi.entities;

import br.edu.atitus.inventoryapi.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory")
public class InventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32, updatable = false)
    private ProductType productType;

    @Column(name = "quantity_on_hand", nullable = false)
    private long quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private long quantityReserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private long lowStockThreshold;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryEntity() {
    }

    public InventoryEntity(Long productId, ProductType productType) {
        this.productId = productId;
        this.productType = productType;
        this.quantityOnHand = 0;
        this.quantityReserved = 0;
        this.lowStockThreshold = 0;
    }

    public long getAvailableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public void addToPhysicalQuantity(long delta) {
        this.quantityOnHand = Math.addExact(quantityOnHand, delta);
        this.updatedAt = Instant.now();
    }

    public void setLowStockThreshold(long lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public long getQuantityOnHand() {
        return quantityOnHand;
    }

    public long getQuantityReserved() {
        return quantityReserved;
    }

    public long getLowStockThreshold() {
        return lowStockThreshold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
