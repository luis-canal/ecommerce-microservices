package br.edu.atitus.analyticsapi.enums;

import java.math.BigDecimal;

/**
 * Faixas de preço pelo preço unitário do item (limite superior inclusivo).
 */
public enum PriceRange {
    UP_TO_50("Até R$ 50", null, new BigDecimal("50.00")),
    FROM_50_TO_150("R$ 50 – R$ 150", new BigDecimal("50.00"), new BigDecimal("150.00")),
    FROM_150_TO_300("R$ 150 – R$ 300", new BigDecimal("150.00"), new BigDecimal("300.00")),
    FROM_300_TO_600("R$ 300 – R$ 600", new BigDecimal("300.00"), new BigDecimal("600.00")),
    ABOVE_600("Acima de R$ 600", new BigDecimal("600.00"), null);

    private final String label;
    private final BigDecimal minExclusive;
    private final BigDecimal maxInclusive;

    PriceRange(String label, BigDecimal minExclusive, BigDecimal maxInclusive) {
        this.label = label;
        this.minExclusive = minExclusive;
        this.maxInclusive = maxInclusive;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getMinExclusive() {
        return minExclusive;
    }

    public BigDecimal getMaxInclusive() {
        return maxInclusive;
    }

    public static PriceRange of(BigDecimal unitPrice) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        for (PriceRange range : values()) {
            if (range.maxInclusive == null || price.compareTo(range.maxInclusive) <= 0) {
                return range;
            }
        }
        return ABOVE_600;
    }
}
