package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.clients.PtaxClient;
import br.edu.atitus.currencyapi.dtos.PtaxQuote;
import br.edu.atitus.currencyapi.dtos.PtaxResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PtaxQuoteServiceTest {

    @Mock
    private PtaxClient ptaxClient;

    @Test
    void selectsLatestPositiveSellQuoteForRequestedDate() {
        var date = LocalDate.of(2024, 1, 2);
        when(ptaxClient.getQuotes("USD", "01-02-2024")).thenReturn(new PtaxResponse(List.of(
                quote("5.00", "2024-01-02 10:00:00.000"),
                quote("5.10", "2024-01-02 13:00:00.000"),
                quote("5.20", "2024-01-01 16:00:00.000"),
                quote("0.00", "2024-01-02 16:00:00.000")
        )));
        var service = new PtaxQuoteService(ptaxClient);

        var result = service.getLatestSellQuote("usd", date);

        assertEquals(new BigDecimal("5.10"), result.orElseThrow());
        verify(ptaxClient).getQuotes("USD", "01-02-2024");
    }

    @Test
    void returnsEmptyWhenBcbHasNoQuotesForTheRequestedDate() {
        var date = LocalDate.of(2024, 1, 2);
        when(ptaxClient.getQuotes("EUR", "01-02-2024")).thenReturn(new PtaxResponse(List.of(
                quote("5.20", "2024-01-01 16:00:00.000")
        )));
        var service = new PtaxQuoteService(ptaxClient);

        assertTrue(service.getLatestSellQuote("EUR", date).isEmpty());
    }

    private PtaxQuote quote(String sell, String dateTime) {
        return new PtaxQuote(null, new BigDecimal(sell), dateTime, "Fechamento");
    }
}
