package br.edu.atitus.orderapi.servicesimpl;

import br.edu.atitus.orderapi.services.ShippingCostCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Component
public class RegionalShippingCostCalculator implements ShippingCostCalculator {

    private static final BigDecimal SOUTH = new BigDecimal("20.00");
    private static final BigDecimal SOUTHEAST = new BigDecimal("25.00");
    private static final BigDecimal CENTRAL_WEST = new BigDecimal("30.00");
    private static final BigDecimal NORTHEAST = new BigDecimal("35.00");
    private static final BigDecimal NORTH = new BigDecimal("40.00");

    private static final Map<String, BigDecimal> STATE_FEES = Map.ofEntries(
            Map.entry("PR", SOUTH), Map.entry("SC", SOUTH), Map.entry("RS", SOUTH),
            Map.entry("ES", SOUTHEAST), Map.entry("MG", SOUTHEAST),
            Map.entry("RJ", SOUTHEAST), Map.entry("SP", SOUTHEAST),
            Map.entry("DF", CENTRAL_WEST), Map.entry("GO", CENTRAL_WEST),
            Map.entry("MT", CENTRAL_WEST), Map.entry("MS", CENTRAL_WEST),
            Map.entry("AL", NORTHEAST), Map.entry("BA", NORTHEAST),
            Map.entry("CE", NORTHEAST), Map.entry("MA", NORTHEAST),
            Map.entry("PB", NORTHEAST), Map.entry("PE", NORTHEAST),
            Map.entry("PI", NORTHEAST), Map.entry("RN", NORTHEAST),
            Map.entry("SE", NORTHEAST),
            Map.entry("AC", NORTH), Map.entry("AP", NORTH), Map.entry("AM", NORTH),
            Map.entry("PA", NORTH), Map.entry("RO", NORTH), Map.entry("RR", NORTH),
            Map.entry("TO", NORTH)
    );

    @Override
    public BigDecimal calculate(String state) {
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UF do endereço de entrega é obrigatória.");
        }
        BigDecimal amount = STATE_FEES.get(state.trim().toUpperCase(Locale.ROOT));
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UF de entrega inválida.");
        }
        return amount;
    }
}
