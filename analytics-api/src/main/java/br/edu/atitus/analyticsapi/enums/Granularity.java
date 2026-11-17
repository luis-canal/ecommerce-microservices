package br.edu.atitus.analyticsapi.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum Granularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlUnit;

    Granularity(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    public String getSqlUnit() {
        return sqlUnit;
    }

    /** Início do período (semana ISO começa na segunda-feira, igual ao date_trunc do PostgreSQL). */
    public LocalDate truncate(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public LocalDate next(LocalDate periodStart) {
        return switch (this) {
            case DAY -> periodStart.plusDays(1);
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
    }
}
