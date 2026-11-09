package br.edu.atitus.orderapi.servicesimpl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalShippingCostCalculatorTest {

    private final RegionalShippingCostCalculator calculator = new RegionalShippingCostCalculator();

    @Test
    void calculatesTheFixedFeeByBrazilianRegion() {
        assertEquals(new BigDecimal("20.00"), calculator.calculate("RS"));
        assertEquals(new BigDecimal("25.00"), calculator.calculate("sp"));
        assertEquals(new BigDecimal("30.00"), calculator.calculate("DF"));
        assertEquals(new BigDecimal("35.00"), calculator.calculate("BA"));
        assertEquals(new BigDecimal("40.00"), calculator.calculate("AM"));
    }

    @Test
    void rejectsUnknownState() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> calculator.calculate("XX")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
