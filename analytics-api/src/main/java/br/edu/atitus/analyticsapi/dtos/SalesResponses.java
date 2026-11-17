package br.edu.atitus.analyticsapi.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contratos de resposta dos indicadores de vendas. Todos os valores monetários estão em BRL
 * e consideram somente pedidos CONFIRMED. Listas são sempre retornadas (nunca null) para
 * consumo direto por gráficos.
 */
public final class SalesResponses {

    public static final String CURRENCY = "BRL";

    private SalesResponses() {
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Filters(String state, String region, Long categoryId, Long productId) {
        public static Filters of(AnalyticsFilter filter) {
            return new Filters(
                    filter.state(),
                    filter.region() == null ? null : filter.region().name(),
                    filter.categoryId(),
                    filter.productId()
            );
        }
    }

    public record Metrics(BigDecimal revenue, long orders, long units, BigDecimal averageTicket) {
    }

    public record Overview(
            Period period,
            Filters filters,
            String currency,
            BigDecimal totalRevenue,
            long confirmedOrders,
            BigDecimal averageTicket,
            long unitsSold,
            List<ProductSales> topProducts,
            List<CategorySales> topCategories
    ) {
    }

    public record SalesPoint(LocalDate periodStart, BigDecimal revenue, long orders, long units, BigDecimal averageTicket) {
    }

    public record SalesSeries(
            Period period,
            Filters filters,
            String currency,
            String granularity,
            Metrics totals,
            List<SalesPoint> points
    ) {
    }

    public record RegionSales(
            String region,
            String regionName,
            BigDecimal revenue,
            long orders,
            long units,
            BigDecimal averageTicket,
            BigDecimal revenueShare
    ) {
    }

    public record StateSales(
            String state,
            String region,
            BigDecimal revenue,
            long orders,
            long units,
            BigDecimal averageTicket,
            BigDecimal revenueShare
    ) {
    }

    public record RegionReport(
            Period period,
            Filters filters,
            String currency,
            Metrics totals,
            List<RegionSales> regions,
            List<StateSales> states
    ) {
    }

    public record ProductSales(
            Long productId,
            String productName,
            String brand,
            Long categoryId,
            String categoryName,
            long units,
            BigDecimal revenue,
            long orders
    ) {
    }

    public record TopProducts(Period period, Filters filters, String currency, String rankBy, List<ProductSales> products) {
    }

    public record CategorySales(
            Long categoryId,
            String categoryName,
            BigDecimal revenue,
            long orders,
            long units,
            BigDecimal revenueShare
    ) {
    }

    public record BrandSales(String brand, BigDecimal revenue, long orders, long units, BigDecimal revenueShare) {
    }

    public record CategoryReport(
            Period period,
            Filters filters,
            String currency,
            List<CategorySales> categories,
            List<BrandSales> brands
    ) {
    }

    public record PriceRangeSales(
            String range,
            String label,
            BigDecimal minExclusive,
            BigDecimal maxInclusive,
            long units,
            BigDecimal revenue,
            long orders
    ) {
    }

    public record PriceRangeReport(Period period, Filters filters, String currency, List<PriceRangeSales> ranges) {
    }
}
