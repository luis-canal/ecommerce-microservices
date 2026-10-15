package br.edu.atitus.currencyapi.dtos;

import java.util.List;

public record PtaxResponse(List<PtaxQuote> value) {

    public PtaxResponse {
        value = value == null ? List.of() : List.copyOf(value);
    }
}
