package br.edu.atitus.orderapi.services;

import java.math.BigDecimal;

public interface ShippingCostCalculator {
    BigDecimal calculate(String state);
}
