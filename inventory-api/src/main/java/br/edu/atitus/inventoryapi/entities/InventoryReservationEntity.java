package br.edu.atitus.inventoryapi.entities;

import br.edu.atitus.inventoryapi.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    protected InventoryReservationEntity() {
    }

    public InventoryReservationEntity(Long orderId, Instant expiresAt) {
        this.orderId = orderId;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ACTIVE;
    }

    public void confirm(Instant confirmedAt) {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void release(Instant releasedAt) {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = releasedAt;
    }

    public void expire(Instant expiredAt) {
        this.status = ReservationStatus.EXPIRED;
        this.expiredAt = expiredAt;
    }

    @PrePersist
    private void beforeInsert() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }
}
