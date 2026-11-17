package br.edu.atitus.analyticsapi.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contrato de GET /ws/orders/internal/analytics (order-api). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceOrder(
        Long id,
        Long customerId,
        Instant orderDate,
        Instant updatedAt,
        String status,
        List<Item> items,
        ShippingAddress shippingAddress,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        String currency,
        Instant confirmedAt,
        Instant cancelledAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long id,
            Long productId,
            Integer quantity,
            BigDecimal priceAtPurchase,
            BigDecimal discountAmount,
            BigDecimal subtotal,
            String currencyAtPurchase,
            String productName,
            String productBrand,
            Long categoryId,
            String categoryName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShippingAddress(
            String zipCode,
            String city,
            String state
    ) {
    }
}
