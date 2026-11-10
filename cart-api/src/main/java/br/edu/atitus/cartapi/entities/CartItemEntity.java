package br.edu.atitus.cartapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "product_id"})
})
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "desired_quantity", nullable = false)
    private Integer desiredQuantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItemEntity() {
    }

    public CartItemEntity(CartEntity cart, Long productId, Integer desiredQuantity) {
        this.cart = cart;
        this.productId = productId;
        this.desiredQuantity = desiredQuantity;
        this.addedAt = Instant.now();
        this.updatedAt = this.addedAt;
    }

    public void updateQuantity(Integer desiredQuantity) {
        this.desiredQuantity = desiredQuantity;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CartEntity getCart() {
        return cart;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getDesiredQuantity() {
        return desiredQuantity;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
