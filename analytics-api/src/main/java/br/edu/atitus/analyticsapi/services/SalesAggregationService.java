package br.edu.atitus.analyticsapi.services;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Mantém as agregações diárias. Sempre recalcula por completo os dias afetados a partir
 * dos fatos, o que torna o processo idempotente e corrige mudanças de status
 * (ex.: CONFIRMED -> CANCELLED remove o pedido das vendas daquele dia).
 */
@Service
public class SalesAggregationService {

    private final NamedParameterJdbcTemplate jdbc;

    public SalesAggregationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(Collection<LocalDate> affectedDates) {
        List<Date> dates = affectedDates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(Date::valueOf)
                .toList();
        if (dates.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("dates", dates);

        jdbc.update("DELETE FROM agg_sales_daily WHERE sale_date IN (:dates)", params);
        jdbc.update("""
                INSERT INTO agg_sales_daily (sale_date, state, region, order_count, units_sold, revenue)
                SELECT o.sale_date,
                       COALESCE(o.state, 'NA'),
                       COALESCE(o.region, 'UNKNOWN'),
                       COUNT(*),
                       COALESCE(SUM(u.units), 0),
                       COALESCE(SUM(o.total_amount), 0)
                  FROM analytics_order o
                  LEFT JOIN (SELECT order_id, SUM(quantity) AS units
                               FROM analytics_order_item GROUP BY order_id) u ON u.order_id = o.order_id
                 WHERE o.counts_as_sale AND o.sale_date IN (:dates)
                 GROUP BY o.sale_date, COALESCE(o.state, 'NA'), COALESCE(o.region, 'UNKNOWN')
                """, params);

        jdbc.update("DELETE FROM agg_product_sales_daily WHERE sale_date IN (:dates)", params);
        jdbc.update("""
                INSERT INTO agg_product_sales_daily (sale_date, product_id, state, region, product_name, brand,
                                                     category_id, category_name, order_count, units_sold, revenue)
                SELECT o.sale_date,
                       i.product_id,
                       COALESCE(o.state, 'NA'),
                       MAX(COALESCE(o.region, 'UNKNOWN')),
                       MAX(i.product_name),
                       MAX(i.brand),
                       MAX(i.category_id),
                       MAX(i.category_name),
                       COUNT(DISTINCT o.order_id),
                       SUM(i.quantity),
                       SUM(i.item_total)
                  FROM analytics_order o
                  JOIN analytics_order_item i ON i.order_id = o.order_id
                 WHERE o.counts_as_sale AND o.sale_date IN (:dates)
                 GROUP BY o.sale_date, i.product_id, COALESCE(o.state, 'NA')
                """, params);
    }
}
