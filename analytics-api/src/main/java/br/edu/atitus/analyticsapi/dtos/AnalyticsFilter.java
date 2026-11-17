package br.edu.atitus.analyticsapi.dtos;

import br.edu.atitus.analyticsapi.enums.BrazilRegion;
import br.edu.atitus.analyticsapi.services.OrderFactMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Filtros comuns das consultas. Datas são dias locais (America/Sao_Paulo), ambos inclusivos.
 * Sem período informado, considera os últimos 30 dias.
 */
public record AnalyticsFilter(
        LocalDate from,
        LocalDate to,
        String state,
        BrazilRegion region,
        Long categoryId,
        Long productId
) {

    public static final int DEFAULT_DAYS = 30;
    public static final long MAX_DAYS = 366L * 5;

    public static AnalyticsFilter of(
            LocalDate from,
            LocalDate to,
            String state,
            String region,
            Long categoryId,
            Long productId
    ) {
        LocalDate end = to != null ? to : LocalDate.now(OrderFactMapper.BUSINESS_ZONE);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw badRequest("O parâmetro 'from' deve ser anterior ou igual a 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw badRequest("O período máximo de consulta é de 5 anos.");
        }

        String normalizedState = null;
        if (state != null && !state.isBlank()) {
            normalizedState = BrazilRegion.normalizeState(state);
            if (normalizedState == null) {
                throw badRequest("UF inválida: " + state);
            }
        }

        BrazilRegion parsedRegion = null;
        if (region != null && !region.isBlank()) {
            try {
                parsedRegion = BrazilRegion.valueOf(region.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                throw badRequest("Região inválida: " + region
                        + ". Valores aceitos: NORTE, NORDESTE, CENTRO_OESTE, SUDESTE, SUL.");
            }
        }

        return new AnalyticsFilter(start, end, normalizedState, parsedRegion, categoryId, productId);
    }

    public boolean hasItemFilter() {
        return categoryId != null || productId != null;
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
