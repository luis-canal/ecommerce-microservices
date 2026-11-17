package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fato analítico de pedido (cópia desnormalizada do pedido do order-api, chave = orderId). */
@Entity
@Table(name = "analytics_order")
public class AnalyticsOrderEntity {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    private Long customerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 3)
    private String currency;

    private Instant orderDate;

    @Column(nullable = false)
    private Instant sourceUpdatedAt;

    private Instant confirmedAt;

    private Instant cancelledAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 9)
    private String zipCode;

    @Column(length = 120)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(length = 16)
    private String region;

    private LocalDate saleDate;

    @Column(nullable = false)
    private boolean countsAsSale;

    @Column(nullable = false)
    private Instant collectedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalyticsOrderItemEntity> items = new ArrayList<>();

    public void replaceItems(List<AnalyticsOrderItemEntity> newItems) {
        items.clear();
        for (AnalyticsOrderItemEntity item : newItems) {
            item.setOrder(this);
            items.add(item);
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
    }

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(Instant sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getShippingAmount() {
        return shippingAmount;
    }

    public void setShippingAmount(BigDecimal shippingAmount) {
        this.shippingAmount = shippingAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }

    public boolean isCountsAsSale() {
        return countsAsSale;
    }

    public void setCountsAsSale(boolean countsAsSale) {
        this.countsAsSale = countsAsSale;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public List<AnalyticsOrderItemEntity> getItems() {
        return items;
    }
}
