package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.dtos.InventoryResponses;
import br.edu.atitus.analyticsapi.dtos.SalesResponses;
import br.edu.atitus.analyticsapi.enums.Granularity;
import br.edu.atitus.analyticsapi.repositories.InventorySnapshotRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Indicadores de estoque a partir dos snapshots e das cópias coletadas do inventory-api. */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final InventorySnapshotRepository snapshotRepository;

    public InventoryQueryService(NamedParameterJdbcTemplate jdbc, InventorySnapshotRepository snapshotRepository) {
        this.jdbc = jdbc;
        this.snapshotRepository = snapshotRepository;
    }

    public InventoryResponses.Report report(LocalDate from, LocalDate to, Granularity granularity, int listLimit) {
        InventoryResponses.Summary summary = snapshotRepository.findFirstByOrderByCapturedAtDesc()
                .map(snapshot -> new InventoryResponses.Summary(
                        snapshot.getCapturedAt(),
                        snapshot.getInventoryCount(),
                        snapshot.getQuantityOnHand(),
                        snapshot.getQuantityReserved(),
                        snapshot.getAvailableQuantity(),
                        snapshot.getOutOfStockCount(),
                        snapshot.getLowStockCount()))
                .orElse(new InventoryResponses.Summary(null, 0, 0, 0, 0, 0, 0));

        MapSqlParameterSource params = window(from, to).addValue("limit", listLimit);

        List<InventoryResponses.ProductStock> outOfStock = jdbc.query("""
                SELECT * FROM inventory_product_status WHERE out_of_stock
                 ORDER BY product_id LIMIT :limit
                """, params, (rs, rowNum) -> productStock(rs));
        List<InventoryResponses.ProductStock> lowStock = jdbc.query("""
                SELECT * FROM inventory_product_status WHERE low_stock
                 ORDER BY available_quantity, product_id LIMIT :limit
                """, params, (rs, rowNum) -> productStock(rs));

        String unit = granularity.getSqlUnit();
        List<InventoryResponses.AvailabilityPoint> history = jdbc.query("""
                SELECT DISTINCT ON (period_start) period_start, captured_at, quantity_on_hand, quantity_reserved,
                       available_quantity, out_of_stock_count, low_stock_count
                  FROM (SELECT CAST(date_trunc('%s', captured_at AT TIME ZONE 'America/Sao_Paulo') AS date) AS period_start, s.*
                          FROM inventory_snapshot s
                         WHERE s.captured_at >= :start AND s.captured_at < :end) x
                 ORDER BY period_start, captured_at DESC
                """.formatted(unit), params, (rs, rowNum) -> new InventoryResponses.AvailabilityPoint(
                rs.getDate("period_start").toLocalDate(),
                rs.getTimestamp("captured_at").toInstant(),
                rs.getLong("quantity_on_hand"),
                rs.getLong("quantity_reserved"),
                rs.getLong("available_quantity"),
                rs.getLong("out_of_stock_count"),
                rs.getLong("low_stock_count")
        ));

        List<InventoryResponses.MovementTypeTotal> totals = jdbc.query("""
                SELECT movement_type, COUNT(*) AS movements, COALESCE(SUM(quantity_delta), 0) AS quantity
                  FROM inventory_movement
                 WHERE created_at >= :start AND created_at < :end
                 GROUP BY movement_type ORDER BY movement_type
                """, params, (rs, rowNum) -> new InventoryResponses.MovementTypeTotal(
                rs.getString("movement_type"), rs.getLong("movements"), rs.getLong("quantity")));

        return new InventoryResponses.Report(
                new SalesResponses.Period(from, to), granularity.name(), summary, outOfStock, lowStock, history, totals);
    }

    public InventoryResponses.MovementPage movements(
            LocalDate from,
            LocalDate to,
            Long productId,
            String movementType,
            int page,
            int size
    ) {
        MapSqlParameterSource params = window(from, to)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        StringBuilder where = new StringBuilder(" WHERE created_at >= :start AND created_at < :end");
        if (productId != null) {
            where.append(" AND product_id = :productId");
            params.addValue("productId", productId);
        }
        if (movementType != null && !movementType.isBlank()) {
            where.append(" AND movement_type = :movementType");
            params.addValue("movementType", movementType.trim().toUpperCase());
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement" + where, params, Long.class);
        List<InventoryResponses.Movement> content = jdbc.query(
                "SELECT * FROM inventory_movement" + where + " ORDER BY created_at DESC, movement_id DESC"
                        + " LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> new InventoryResponses.Movement(
                        rs.getLong("movement_id"),
                        rs.getLong("product_id"),
                        rs.getString("movement_type"),
                        rs.getLong("quantity_delta"),
                        rs.getString("reason"),
                        (Long) rs.getObject("order_id", Long.class),
                        (Long) rs.getObject("reservation_id", Long.class),
                        rs.getTimestamp("created_at").toInstant()
                ));
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new InventoryResponses.MovementPage(
                new SalesResponses.Period(from, to), content, page, size, totalElements, totalPages);
    }

    private static MapSqlParameterSource window(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(OrderFactMapper.BUSINESS_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(OrderFactMapper.BUSINESS_ZONE).toInstant();
        return new MapSqlParameterSource()
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
    }

    private static InventoryResponses.ProductStock productStock(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("source_updated_at");
        return new InventoryResponses.ProductStock(
                rs.getLong("product_id"),
                rs.getString("product_type"),
                rs.getLong("quantity_on_hand"),
                rs.getLong("quantity_reserved"),
                rs.getLong("available_quantity"),
                rs.getLong("low_stock_threshold"),
                rs.getBoolean("low_stock"),
                rs.getBoolean("out_of_stock"),
                updatedAt == null ? null : updatedAt.toInstant()
        );
    }
}
