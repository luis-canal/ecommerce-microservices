package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.entities.AnalyticsOrderEntity;
import br.edu.atitus.analyticsapi.enums.BrazilRegion;
import br.edu.atitus.analyticsapi.enums.PriceRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFactMapperTest {

    private final OrderFactMapper mapper = new OrderFactMapper();

    @Test
    void mapsConfirmedBrlOrderUsingSaoPauloDateRegionAndPriceRange() {
        // 02:30 UTC do dia 2 ainda é dia 1 em America/Sao_Paulo (UTC-3).
        Instant confirmedAt = Instant.parse("2026-03-02T02:30:00Z");
        SourceOrder source = new SourceOrder(
                7L, 3L, confirmedAt.minusSeconds(600), confirmedAt, "CONFIRMED",
                List.of(new SourceOrder.Item(70L, 100L, 2, new BigDecimal("150.00"), BigDecimal.ZERO,
                        new BigDecimal("300.00"), "BRL", "Tênis", "Marca", 5L, "Calçados")),
                new SourceOrder.ShippingAddress("90000-000", "Porto Alegre", "rs"),
                new BigDecimal("300.00"), BigDecimal.ZERO, new BigDecimal("20.00"), new BigDecimal("320.00"),
                "BRL", confirmedAt, null
        );

        AnalyticsOrderEntity entity = new AnalyticsOrderEntity();
        mapper.apply(source, entity, Instant.now());

        assertThat(entity.isCountsAsSale()).isTrue();
        assertThat(entity.getSaleDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(entity.getState()).isEqualTo("RS");
        assertThat(entity.getRegion()).isEqualTo(BrazilRegion.SUL.name());
        assertThat(entity.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getPriceRange()).isEqualTo(PriceRange.FROM_50_TO_150.name());
            assertThat(item.getItemTotal()).isEqualByComparingTo("300.00");
        });
    }

    @Test
    void nonConfirmedOrNonBrlOrdersAreNotSales() {
        AnalyticsOrderEntity cancelled = new AnalyticsOrderEntity();
        mapper.apply(order("CANCELLED", "BRL"), cancelled, Instant.now());
        AnalyticsOrderEntity usd = new AnalyticsOrderEntity();
        mapper.apply(order("CONFIRMED", "USD"), usd, Instant.now());

        assertThat(cancelled.isCountsAsSale()).isFalse();
        assertThat(usd.isCountsAsSale()).isFalse();
    }

    @Test
    void mapsStatesToIbgeRegionsAndPricesToRanges() {
        assertThat(BrazilRegion.fromState("SP")).isEqualTo(BrazilRegion.SUDESTE);
        assertThat(BrazilRegion.fromState("df")).isEqualTo(BrazilRegion.CENTRO_OESTE);
        assertThat(BrazilRegion.fromState("BA")).isEqualTo(BrazilRegion.NORDESTE);
        assertThat(BrazilRegion.fromState("AM")).isEqualTo(BrazilRegion.NORTE);
        assertThat(BrazilRegion.fromState("XX")).isEqualTo(BrazilRegion.UNKNOWN);
        assertThat(BrazilRegion.fromState(null)).isEqualTo(BrazilRegion.UNKNOWN);

        assertThat(PriceRange.of(new BigDecimal("50.00"))).isEqualTo(PriceRange.UP_TO_50);
        assertThat(PriceRange.of(new BigDecimal("50.01"))).isEqualTo(PriceRange.FROM_50_TO_150);
        assertThat(PriceRange.of(new BigDecimal("300.00"))).isEqualTo(PriceRange.FROM_150_TO_300);
        assertThat(PriceRange.of(new BigDecimal("600.00"))).isEqualTo(PriceRange.FROM_300_TO_600);
        assertThat(PriceRange.of(new BigDecimal("600.01"))).isEqualTo(PriceRange.ABOVE_600);
    }

    private static SourceOrder order(String status, String currency) {
        Instant now = Instant.parse("2026-03-10T15:00:00Z");
        return new SourceOrder(1L, 1L, now, now, status, List.of(), null,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, currency, now, null);
    }
}
