package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.dtos.AnalyticsFilter;
import br.edu.atitus.analyticsapi.dtos.SalesResponses;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.BrandSales;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.CategorySales;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.Filters;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.Metrics;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.Period;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.PriceRangeSales;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.ProductSales;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.RegionSales;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.SalesPoint;
import br.edu.atitus.analyticsapi.dtos.SalesResponses.StateSales;
import br.edu.atitus.analyticsapi.enums.BrazilRegion;
import br.edu.atitus.analyticsapi.enums.Granularity;
import br.edu.atitus.analyticsapi.enums.PriceRange;
import br.edu.atitus.analyticsapi.enums.ProductRanking;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas dos indicadores de vendas. Lê somente o analytics_db: as agregações diárias
 * (agg_*) quando não há filtro por produto/categoria, e os fatos desnormalizados quando há.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ overview

    public SalesResponses.Overview overview(AnalyticsFilter filter) {
        Metrics totals = totals(filter);
        return new SalesResponses.Overview(
                period(filter),
                Filters.of(filter),
                SalesResponses.CURRENCY,
                totals.revenue(),
                totals.orders(),
                totals.averageTicket(),
                totals.units(),
                topProducts(filter, ProductRanking.UNITS, 5),
                categoryRows(filter, 5)
        );
    }

    // ------------------------------------------------------------------ evolução

    public SalesResponses.SalesSeries salesSeries(AnalyticsFilter filter, Granularity granularity) {
        String dimension = "CAST(date_trunc('" + granularity.getSqlUnit() + "', a.sale_date) AS date)";
        Map<LocalDate, MetricRow> rows = new HashMap<>();
        for (MetricRow row : orderMetrics(filter, dimension)) {
            rows.put(((Date) row.key()).toLocalDate(), row);
        }

        List<SalesPoint> points = new ArrayList<>();
        LocalDate cursor = granularity.truncate(filter.from());
        while (!cursor.isAfter(filter.to())) {
            MetricRow row = rows.get(cursor);
            points.add(row == null
                    ? new SalesPoint(cursor, zero(), 0, 0, zero())
                    : new SalesPoint(cursor, row.revenue(), row.orders(), row.units(), ticket(row.revenue(), row.orders())));
            cursor = granularity.next(cursor);
        }

        return new SalesResponses.SalesSeries(
                period(filter),
                Filters.of(filter),
                SalesResponses.CURRENCY,
                granularity.name(),
                totals(filter),
                points
        );
    }

    // ------------------------------------------------------------------ geografia

    public SalesResponses.RegionReport byRegion(AnalyticsFilter filter) {
        Metrics totals = totals(filter);

        Map<BrazilRegion, MetricRow> byRegion = new EnumMap<>(BrazilRegion.class);
        for (MetricRow row : orderMetrics(filter, "a.region")) {
            byRegion.put(parseRegion((String) row.key()), row);
        }
        List<RegionSales> regions = new ArrayList<>();
        for (BrazilRegion region : BrazilRegion.values()) {
            MetricRow row = byRegion.get(region);
            if (row == null && region == BrazilRegion.UNKNOWN) {
                continue;
            }
            if (row == null && filter.region() != null && filter.region() != region) {
                continue;
            }
            if (row == null && filter.state() != null && !region.getStates().contains(filter.state())) {
                continue;
            }
            regions.add(row == null
                    ? new RegionSales(region.name(), region.getDisplayName(), zero(), 0, 0, zero(), zero())
                    : new RegionSales(region.name(), region.getDisplayName(), row.revenue(), row.orders(), row.units(),
                            ticket(row.revenue(), row.orders()), share(row.revenue(), totals.revenue())));
        }

        List<StateSales> states = orderMetrics(filter, "a.state").stream()
                .map(row -> {
                    String state = (String) row.key();
                    return new StateSales(state, BrazilRegion.fromState(state).name(), row.revenue(), row.orders(),
                            row.units(), ticket(row.revenue(), row.orders()), share(row.revenue(), totals.revenue()));
                })
                .sorted((left, right) -> right.revenue().compareTo(left.revenue()))
                .toList();

        return new SalesResponses.RegionReport(period(filter), Filters.of(filter), SalesResponses.CURRENCY,
                totals, regions, states);
    }

    // ------------------------------------------------------------------ produtos

    public SalesResponses.TopProducts topProductsReport(AnalyticsFilter filter, ProductRanking ranking, int limit) {
        return new SalesResponses.TopProducts(period(filter), Filters.of(filter), SalesResponses.CURRENCY,
                ranking.name(), topProducts(filter, ranking, limit));
    }

    public SalesResponses.CategoryReport categories(AnalyticsFilter filter, int limit) {
        return new SalesResponses.CategoryReport(period(filter), Filters.of(filter), SalesResponses.CURRENCY,
                categoryRows(filter, limit), brandRows(filter, limit));
    }

    public SalesResponses.PriceRangeReport priceRanges(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = itemWhere(filter, params);
        Map<String, PriceRangeSales> found = new HashMap<>();
        jdbc.query("""
                SELECT i.price_range AS k, COALESCE(SUM(i.quantity), 0) AS units,
                       COALESCE(SUM(i.item_total), 0) AS revenue, COUNT(DISTINCT o.order_id) AS orders
                  FROM analytics_order o JOIN analytics_order_item i ON i.order_id = o.order_id
                """ + where + " GROUP BY i.price_range", params, rs -> {
            PriceRange range = PriceRange.valueOf(rs.getString("k"));
            found.put(range.name(), new PriceRangeSales(range.name(), range.getLabel(), range.getMinExclusive(),
                    range.getMaxInclusive(), rs.getLong("units"), money(rs.getBigDecimal("revenue")), rs.getLong("orders")));
        });

        List<PriceRangeSales> ranges = new ArrayList<>();
        for (PriceRange range : PriceRange.values()) {
            ranges.add(found.getOrDefault(range.name(), new PriceRangeSales(range.name(), range.getLabel(),
                    range.getMinExclusive(), range.getMaxInclusive(), 0, zero(), 0)));
        }
        return new SalesResponses.PriceRangeReport(period(filter), Filters.of(filter), SalesResponses.CURRENCY, ranges);
    }

    // ------------------------------------------------------------------ helpers

    Metrics totals(AnalyticsFilter filter) {
        List<MetricRow> rows = orderMetrics(filter, null);
        MetricRow row = rows.isEmpty() ? new MetricRow(null, 0, 0, zero()) : rows.getFirst();
        return new Metrics(row.revenue(), row.orders(), row.units(), ticket(row.revenue(), row.orders()));
    }

    private List<ProductSales> topProducts(AnalyticsFilter filter, ProductRanking ranking, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(filter.from()))
                .addValue("to", Date.valueOf(filter.to()))
                .addValue("limit", limit);
        StringBuilder where = new StringBuilder(" WHERE a.sale_date BETWEEN :from AND :to");
        appendLocationFilters(filter, params, where, "a.state", "a.region");
        if (filter.categoryId() != null) {
            where.append(" AND a.category_id = :categoryId");
            params.addValue("categoryId", filter.categoryId());
        }
        if (filter.productId() != null) {
            where.append(" AND a.product_id = :productId");
            params.addValue("productId", filter.productId());
        }
        String order = ranking == ProductRanking.REVENUE ? "revenue DESC, units DESC" : "units DESC, revenue DESC";
        return jdbc.query("""
                SELECT a.product_id, MAX(a.product_name) AS product_name, MAX(a.brand) AS brand,
                       MAX(a.category_id) AS category_id, MAX(a.category_name) AS category_name,
                       SUM(a.units_sold) AS units, SUM(a.revenue) AS revenue, SUM(a.order_count) AS orders
                  FROM agg_product_sales_daily a
                """ + where + " GROUP BY a.product_id ORDER BY " + order + ", a.product_id LIMIT :limit",
                params, (rs, rowNum) -> new ProductSales(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("brand"),
                        (Long) rs.getObject("category_id", Long.class),
                        rs.getString("category_name"),
                        rs.getLong("units"),
                        money(rs.getBigDecimal("revenue")),
                        rs.getLong("orders")
                ));
    }

    private List<CategorySales> categoryRows(AnalyticsFilter filter, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        String where = itemWhere(filter, params);
        List<CategorySales> raw = jdbc.query("""
                SELECT i.category_id, MAX(i.category_name) AS category_name, COALESCE(SUM(i.item_total), 0) AS revenue,
                       COUNT(DISTINCT o.order_id) AS orders, COALESCE(SUM(i.quantity), 0) AS units
                  FROM analytics_order o JOIN analytics_order_item i ON i.order_id = o.order_id
                """ + where + " GROUP BY i.category_id ORDER BY revenue DESC, i.category_id LIMIT :limit",
                params, (rs, rowNum) -> new CategorySales(
                        (Long) rs.getObject("category_id", Long.class),
                        rs.getString("category_name"),
                        money(rs.getBigDecimal("revenue")),
                        rs.getLong("orders"),
                        rs.getLong("units"),
                        null
                ));
        BigDecimal total = itemRevenue(filter);
        return raw.stream()
                .map(row -> new CategorySales(row.categoryId(), row.categoryName(), row.revenue(), row.orders(),
                        row.units(), share(row.revenue(), total)))
                .toList();
    }

    private List<BrandSales> brandRows(AnalyticsFilter filter, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        String where = itemWhere(filter, params);
        BigDecimal total = itemRevenue(filter);
        return jdbc.query("""
                SELECT COALESCE(i.brand, 'Sem marca') AS brand, COALESCE(SUM(i.item_total), 0) AS revenue,
                       COUNT(DISTINCT o.order_id) AS orders, COALESCE(SUM(i.quantity), 0) AS units
                  FROM analytics_order o JOIN analytics_order_item i ON i.order_id = o.order_id
                """ + where + " GROUP BY COALESCE(i.brand, 'Sem marca') ORDER BY revenue DESC, brand LIMIT :limit",
                params, (rs, rowNum) -> new BrandSales(
                        rs.getString("brand"),
                        money(rs.getBigDecimal("revenue")),
                        rs.getLong("orders"),
                        rs.getLong("units"),
                        share(money(rs.getBigDecimal("revenue")), total)
                ));
    }

    private BigDecimal itemRevenue(AnalyticsFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = itemWhere(filter, params);
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.item_total), 0)
                  FROM analytics_order o JOIN analytics_order_item i ON i.order_id = o.order_id
                """ + where, params, BigDecimal.class);
        return money(value);
    }

    /**
     * Métricas por pedido (faturamento = totalAmount). Com filtro de produto/categoria,
     * o faturamento passa a ser a soma dos itens filtrados e os pedidos são contados uma única vez.
     */
    private List<MetricRow> orderMetrics(AnalyticsFilter filter, String dimension) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(filter.from()))
                .addValue("to", Date.valueOf(filter.to()));
        String source;
        String metrics;
        if (!filter.hasItemFilter()) {
            StringBuilder where = new StringBuilder(" WHERE sale_date BETWEEN :from AND :to");
            appendLocationFilters(filter, params, where, "state", "region");
            source = "SELECT sale_date, state, region, order_count AS orders, units_sold AS units, revenue "
                    + "FROM agg_sales_daily" + where;
            metrics = "COALESCE(SUM(a.orders), 0) AS orders, COALESCE(SUM(a.units), 0) AS units, "
                    + "COALESCE(SUM(a.revenue), 0) AS revenue";
        } else {
            String where = itemWhere(filter, params);
            source = "SELECT o.sale_date, COALESCE(o.state, 'NA') AS state, COALESCE(o.region, 'UNKNOWN') AS region, "
                    + "o.order_id, i.quantity AS units, i.item_total AS revenue "
                    + "FROM analytics_order o JOIN analytics_order_item i ON i.order_id = o.order_id" + where;
            metrics = "COUNT(DISTINCT a.order_id) AS orders, COALESCE(SUM(a.units), 0) AS units, "
                    + "COALESCE(SUM(a.revenue), 0) AS revenue";
        }

        String sql = dimension == null
                ? "SELECT NULL AS k, " + metrics + " FROM (" + source + ") a"
                : "SELECT " + dimension + " AS k, " + metrics + " FROM (" + source + ") a GROUP BY " + dimension;
        return jdbc.query(sql, params, (rs, rowNum) -> new MetricRow(
                rs.getObject("k"),
                rs.getLong("orders"),
                rs.getLong("units"),
                money(rs.getBigDecimal("revenue"))
        ));
    }

    private String itemWhere(AnalyticsFilter filter, MapSqlParameterSource params) {
        params.addValue("from", Date.valueOf(filter.from()));
        params.addValue("to", Date.valueOf(filter.to()));
        StringBuilder where = new StringBuilder(" WHERE o.counts_as_sale AND o.sale_date BETWEEN :from AND :to");
        appendLocationFilters(filter, params, where, "o.state", "o.region");
        if (filter.categoryId() != null) {
            where.append(" AND i.category_id = :categoryId");
            params.addValue("categoryId", filter.categoryId());
        }
        if (filter.productId() != null) {
            where.append(" AND i.product_id = :productId");
            params.addValue("productId", filter.productId());
        }
        return where.toString();
    }

    private static void appendLocationFilters(
            AnalyticsFilter filter,
            MapSqlParameterSource params,
            StringBuilder where,
            String stateColumn,
            String regionColumn
    ) {
        if (filter.state() != null) {
            where.append(" AND ").append(stateColumn).append(" = :state");
            params.addValue("state", filter.state());
        }
        if (filter.region() != null) {
            where.append(" AND ").append(regionColumn).append(" = :region");
            params.addValue("region", filter.region().name());
        }
    }

    private static Period period(AnalyticsFilter filter) {
        return new Period(filter.from(), filter.to());
    }

    private static BrazilRegion parseRegion(String value) {
        try {
            return value == null ? BrazilRegion.UNKNOWN : BrazilRegion.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return BrazilRegion.UNKNOWN;
        }
    }

    static BigDecimal ticket(BigDecimal revenue, long orders) {
        return orders == 0 ? zero() : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    /** Participação percentual (0–100) com duas casas. */
    static BigDecimal share(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return zero();
        }
        return value.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? zero() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    record MetricRow(Object key, long orders, long units, BigDecimal revenue) {
    }
}
