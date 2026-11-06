package br.edu.atitus.inventoryapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_reservation_item")
public class InventoryReservationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservationEntity reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", nullable = false)
    private InventoryEntity inventory;

    @Column(nullable = false)
    private long quantity;

    protected InventoryReservationItemEntity() {
    }

    public InventoryReservationItemEntity(
            InventoryReservationEntity reservation,
            InventoryEntity inventory,
            long quantity
    ) {
        this.reservation = reservation;
        this.inventory = inventory;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return inventory.getProductId();
    }

    public InventoryEntity getInventory() {
        return inventory;
    }

    public long getQuantity() {
        return quantity;
    }
}
