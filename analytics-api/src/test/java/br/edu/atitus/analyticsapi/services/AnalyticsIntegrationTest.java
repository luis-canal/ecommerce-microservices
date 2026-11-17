package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.OrderApiClient;
import br.edu.atitus.analyticsapi.clients.PageResponse;
import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.dtos.AnalyticsFilter;
import br.edu.atitus.analyticsapi.enums.Granularity;
import br.edu.atitus.analyticsapi.enums.PriceRange;
import br.edu.atitus.analyticsapi.enums.ProductRanking;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import br.edu.atitus.analyticsapi.repositories.CollectionCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:",
        "analytics.collection.enabled=false",
        "analytics.collection.page-size=2"
})
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 10);
    private static final Instant CONFIRMED_AT = Instant.parse("2026-05-10T15:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private OrderApiClient orderApiClient;

    @Autowired
    private OrderCollector orderCollector;

    @Autowired
    private AnalyticsQueryService queryService;

    @Autowired
    private CollectionCheckpointRepository checkpointRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private CollectionService collectionService;

    @BeforeEach
    void cleanDatabase() {
        reset(orderApiClient);
        jdbcTemplate.execute("""
                TRUNCATE TABLE analytics_order_item, analytics_order, agg_sales_daily, agg_product_sales_daily,
                collection_checkpoint, collection_run RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void repeatedCollectionIsIdempotent() {
        List<SourceOrder> orders = List.of(
                order(1L, "CONFIRMED", "SP", CONFIRMED_AT, item(10L, 2, "40.00", 1L)),
                order(2L, "CONFIRMED", "RS", CONFIRMED_AT.plusSeconds(10), item(11L, 1, "700.00", 2L)),
                order(3L, "CANCELLED", "SP", CONFIRMED_AT.plusSeconds(20), item(10L, 5, "40.00", 1L))
        );
        serve(orders);

        orderCollector.collect();
        orderCollector.collect();
        orderCollector.collect();

        assertThat(count("analytics_order")).isEqualTo(3);
        assertThat(count("analytics_order_item")).isEqualTo(3);

        AnalyticsFilter filter = AnalyticsFilter.of(DAY, DAY, null, null, null, null);
        var overview = queryService.overview(filter);
        assertThat(overview.confirmedOrders()).isEqualTo(2);
        assertThat(overview.totalRevenue()).isEqualByComparingTo("780.00");
        assertThat(overview.unitsSold()).isEqualTo(3);
        assertThat(overview.averageTicket()).isEqualByComparingTo("390.00");
        // Maior updatedAt (+20 s) menos a margem de segurança padrão de 5 s.
        assertThat(checkpointRepository.findById("ORDERS").orElseThrow().getCursorAt())
                .isEqualTo(CONFIRMED_AT.plusSeconds(15));
    }

    @Test
    void collectionStatusExposesTheTwentyMostRecentRuns() {
        Long lastRun = null;
        for (int i = 0; i < 25; i++) {
            lastRun = checkpointService.start(CollectionSource.ORDERS);
            checkpointService.finish(CollectionSource.ORDERS, lastRun, i, null);
        }
        Long failedRun = checkpointService.start(CollectionSource.INVENTORY_SNAPSHOT);
        checkpointService.finish(CollectionSource.INVENTORY_SNAPSHOT, failedRun, 0,
                new IllegalStateException("inventory-api indisponível"));

        var status = collectionService.status();

        assertThat(status.sources()).extracting("source")
                .containsExactly("ORDERS", "INVENTORY_SNAPSHOT", "INVENTORY_MOVEMENTS");
        assertThat(status.recentRuns()).hasSize(20);
        var newest = status.recentRuns().get(0);
        assertThat(newest.id()).isEqualTo(failedRun);
        assertThat(newest.status()).isEqualTo("FAILED");
        assertThat(newest.errorMessage()).isEqualTo("inventory-api indisponível");
        assertThat(status.recentRuns().get(1).id()).isEqualTo(lastRun);
        assertThat(status.recentRuns().get(1).recordsProcessed()).isEqualTo(24);
    }

    @Test
    void topProductsWithCategoryFilterUsesFactsWhenProductChangedCategoryOnTheSameDay() {
        // Produto 10 vendido na categoria 1 e, no mesmo dia e UF, já recategorizado para a 3.
        // O agregado diário guarda uma só categoria para (dia, produto, UF); os fatos guardam a de cada item.
        serve(List.of(
                order(1L, "CONFIRMED", "SP", CONFIRMED_AT, item(10L, 2, "40.00", 1L)),
                order(2L, "CONFIRMED", "SP", CONFIRMED_AT.plusSeconds(10), item(10L, 1, "40.00", 3L)),
                order(3L, "CONFIRMED", "SP", CONFIRMED_AT.plusSeconds(20), item(11L, 4, "10.00", 1L))
        ));
        orderCollector.collect();

        var category1 = queryService.topProductsReport(
                AnalyticsFilter.of(DAY, DAY, null, null, 1L, null), ProductRanking.UNITS, 10);
        assertThat(category1.products()).extracting("productId").containsExactly(11L, 10L);
        var product10InCategory1 = category1.products().get(1);
        assertThat(product10InCategory1.units()).isEqualTo(2);
        assertThat(product10InCategory1.revenue()).isEqualByComparingTo("80.00");
        assertThat(product10InCategory1.orders()).isEqualTo(1);
        assertThat(product10InCategory1.categoryId()).isEqualTo(1L);

        var category3 = queryService.topProductsReport(
                AnalyticsFilter.of(DAY, DAY, null, null, 3L, null), ProductRanking.UNITS, 10);
        assertThat(category3.products()).singleElement().satisfies(p -> {
            assertThat(p.productId()).isEqualTo(10L);
            assertThat(p.units()).isEqualTo(1);
        });

        // O top 5 do overview usa o mesmo caminho.
        var overview = queryService.overview(AnalyticsFilter.of(DAY, DAY, null, null, 1L, null));
        assertThat(overview.topProducts()).extracting("productId").containsExactly(11L, 10L);

        // Sem filtro de item, continua usando o agregado e soma o produto inteiro.
        var all = queryService.topProductsReport(
                AnalyticsFilter.of(DAY, DAY, null, null, null, null), ProductRanking.UNITS, 10);
        assertThat(all.products()).filteredOn(p -> p.productId() == 10L)
                .singleElement().satisfies(p -> assertThat(p.units()).isEqualTo(3));
    }

    @Test
    void statusChangeRecalculatesAggregates() {
        serve(List.of(order(1L, "CONFIRMED", "SP", CONFIRMED_AT, item(10L, 2, "40.00", 1L))));
        orderCollector.collect();
        AnalyticsFilter filter = AnalyticsFilter.of(DAY, DAY, null, null, null, null);
        assertThat(queryService.overview(filter).confirmedOrders()).isEqualTo(1);

        serve(List.of(order(1L, "CANCELLED", "SP", CONFIRMED_AT.plusSeconds(3600), item(10L, 2, "40.00", 1L))));
        orderCollector.collect();

        var overview = queryService.overview(filter);
        assertThat(overview.confirmedOrders()).isZero();
        assertThat(overview.totalRevenue()).isEqualByComparingTo("0");
        assertThat(count("agg_sales_daily")).isZero();
        assertThat(count("agg_product_sales_daily")).isZero();
    }

    @Test
    void exposesSeriesRegionsProductsCategoriesAndPriceRanges() {
        serve(List.of(
                order(1L, "CONFIRMED", "SP", CONFIRMED_AT, item(10L, 2, "40.00", 1L)),
                order(2L, "CONFIRMED", "RS", CONFIRMED_AT.plusSeconds(10), item(11L, 1, "700.00", 2L)),
                order(3L, "CONFIRMED", "SP", CONFIRMED_AT.plusSeconds(20), item(10L, 1, "40.00", 1L))
        ));
        orderCollector.collect();
        AnalyticsFilter filter = AnalyticsFilter.of(DAY.minusDays(2), DAY, null, null, null, null);

        var series = queryService.salesSeries(filter, Granularity.DAY);
        assertThat(series.points()).hasSize(3);
        assertThat(series.points().get(2).orders()).isEqualTo(3);
        assertThat(series.points().get(0).orders()).isZero();

        var regions = queryService.byRegion(filter);
        assertThat(regions.regions()).filteredOn(r -> r.region().equals("SUDESTE"))
                .singleElement().satisfies(r -> assertThat(r.orders()).isEqualTo(2));
        assertThat(regions.states().getFirst().state()).isEqualTo("RS");

        var top = queryService.topProductsReport(filter, ProductRanking.UNITS, 10);
        assertThat(top.products().getFirst().productId()).isEqualTo(10L);
        assertThat(top.products().getFirst().units()).isEqualTo(3);
        assertThat(top.products().getFirst().orders()).isEqualTo(2);

        var categories = queryService.categories(filter, 10);
        assertThat(categories.categories().getFirst().categoryId()).isEqualTo(2L);

        var ranges = queryService.priceRanges(filter);
        assertThat(ranges.ranges()).hasSize(PriceRange.values().length);
        assertThat(ranges.ranges()).filteredOn(r -> r.range().equals("UP_TO_50"))
                .singleElement().satisfies(r -> assertThat(r.units()).isEqualTo(3));

        var filtered = queryService.overview(AnalyticsFilter.of(DAY, DAY, null, "SUL", null, null));
        assertThat(filtered.confirmedOrders()).isEqualTo(1);
        var byCategory = queryService.overview(AnalyticsFilter.of(DAY, DAY, null, null, 1L, null));
        assertThat(byCategory.totalRevenue()).isEqualByComparingTo("120.00");
    }

    private void serve(List<SourceOrder> orders) {
        doAnswer(invocation -> {
            Instant after = invocation.getArgument(0);
            int page = invocation.getArgument(1);
            int size = invocation.getArgument(2);
            List<SourceOrder> matching = orders.stream().filter(o -> !o.updatedAt().isBefore(after)).toList();
            int fromIndex = Math.min(page * size, matching.size());
            int toIndex = Math.min(fromIndex + size, matching.size());
            int totalPages = (matching.size() + size - 1) / size;
            return PageResponse.of(matching.subList(fromIndex, toIndex), page, totalPages);
        }).when(orderApiClient).fetchOrdersUpdatedAfter(any(), anyInt(), anyInt());
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private static SourceOrder order(Long id, String status, String state, Instant updatedAt, SourceOrder.Item item) {
        BigDecimal total = item.subtotal();
        return new SourceOrder(id, 99L, CONFIRMED_AT, updatedAt, status, List.of(item),
                new SourceOrder.ShippingAddress("00000-000", "Cidade", state),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total, "BRL", CONFIRMED_AT, null);
    }

    private static SourceOrder.Item item(Long productId, int quantity, String unitPrice, Long categoryId) {
        BigDecimal price = new BigDecimal(unitPrice);
        return new SourceOrder.Item(null, productId, quantity, price, BigDecimal.ZERO,
                price.multiply(BigDecimal.valueOf(quantity)), "BRL", "Produto " + productId, "Marca",
                categoryId, "Categoria " + categoryId);
    }
}
