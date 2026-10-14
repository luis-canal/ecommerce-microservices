package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.clients.CurrencyClient;
import br.edu.atitus.productapi.dtos.CurrencyResponse;
import org.springframework.stereotype.Service;

@Service
public class CurrencyClientService {

    private final CurrencyClient currencyClient;

    public CurrencyClientService(CurrencyClient currencyClient) {
        this.currencyClient = currencyClient;
    }

    public CurrencyResponse getCurrency(String source, String target) {
        return currencyClient.getCurrency(source, target);
    }
}
