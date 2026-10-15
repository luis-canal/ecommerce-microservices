package br.edu.atitus.currencyapi.clients;

import br.edu.atitus.currencyapi.dtos.PtaxResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface PtaxClient {

    @GetExchange("/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?@moeda='{currency}'&@dataCotacao='{quoteDate}'&$format=json")
    PtaxResponse getQuotes(
            @PathVariable("currency") String currency,
            @PathVariable("quoteDate") String quoteDate
    );
}
