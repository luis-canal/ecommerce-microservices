package br.edu.atitus.currencyapi.dtos;

import java.math.BigDecimal;

public record PtaxQuote(
        BigDecimal cotacaoCompra,
        BigDecimal cotacaoVenda,
        String dataHoraCotacao,
        String tipoBoletim
) {
}
