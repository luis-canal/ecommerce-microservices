package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.entities.AnalyticsOrderEntity;
import br.edu.atitus.analyticsapi.entities.AnalyticsOrderItemEntity;
import br.edu.atitus.analyticsapi.enums.BrazilRegion;
import br.edu.atitus.analyticsapi.enums.PriceRange;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transforma o pedido operacional no fato analítico: aplica as regras de negócio
 * (venda = CONFIRMED em BRL), data local de venda, região IBGE e faixa de preço.
 */
@Component
public class OrderFactMapper {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    public static final String SALES_CURRENCY = "BRL";
    public static final String CONFIRMED_STATUS = "CONFIRMED";

    public void apply(SourceOrder source, AnalyticsOrderEntity target, Instant collectedAt) {
        String status = source.status() == null ? "UNKNOWN" : source.status().toUpperCase(Locale.ROOT);
        String currency = source.currency() == null ? null : source.currency().toUpperCase(Locale.ROOT);

        target.setOrderId(source.id());
        target.setCustomerId(source.customerId());
        target.setStatus(status);
        target.setCurrency(currency);
        target.setOrderDate(source.orderDate());
        target.setSourceUpdatedAt(source.updatedAt() != null ? source.updatedAt() : collectedAt);
        target.setConfirmedAt(source.confirmedAt());
        target.setCancelledAt(source.cancelledAt());
        target.setSubtotal(money(source.subtotal()));
        target.setDiscountAmount(money(source.discountAmount()));
        target.setShippingAmount(money(source.shippingAmount()));
        target.setTotalAmount(money(source.totalAmount()));

        SourceOrder.ShippingAddress address = source.shippingAddress();
        String state = address == null ? null : BrazilRegion.normalizeState(address.state());
        target.setZipCode(address == null ? null : address.zipCode());
        target.setCity(address == null ? null : address.city());
        target.setState(state);
        target.setRegion(BrazilRegion.fromState(state).name());

        target.setSaleDate(saleDate(source));
        target.setCountsAsSale(CONFIRMED_STATUS.equals(status) && SALES_CURRENCY.equals(currency));
        target.setCollectedAt(collectedAt);
        target.replaceItems(mapItems(source.items()));
    }

    static LocalDate saleDate(SourceOrder source) {
        Instant reference = source.confirmedAt() != null ? source.confirmedAt() : source.orderDate();
        return reference == null ? null : reference.atZone(BUSINESS_ZONE).toLocalDate();
    }

    private List<AnalyticsOrderItemEntity> mapItems(List<SourceOrder.Item> items) {
        List<AnalyticsOrderItemEntity> mapped = new ArrayList<>();
        if (items == null) {
            return mapped;
        }
        for (SourceOrder.Item item : items) {
            if (item == null || item.productId() == null) {
                continue;
            }
            int quantity = item.quantity() == null ? 0 : item.quantity();
            BigDecimal unitPrice = money(item.priceAtPurchase());
            BigDecimal itemTotal = item.subtotal() != null && item.subtotal().signum() > 0
                    ? money(item.subtotal())
                    : unitPrice.multiply(BigDecimal.valueOf(quantity))
                            .subtract(money(item.discountAmount()))
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);

            AnalyticsOrderItemEntity entity = new AnalyticsOrderItemEntity();
            entity.setSourceItemId(item.id());
            entity.setProductId(item.productId());
            entity.setProductName(item.productName());
            entity.setBrand(item.productBrand());
            entity.setCategoryId(item.categoryId());
            entity.setCategoryName(item.categoryName());
            entity.setQuantity(quantity);
            entity.setUnitPrice(unitPrice);
            entity.setItemTotal(itemTotal);
            entity.setPriceRange(PriceRange.of(unitPrice).name());
            mapped.add(entity);
        }
        return mapped;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
