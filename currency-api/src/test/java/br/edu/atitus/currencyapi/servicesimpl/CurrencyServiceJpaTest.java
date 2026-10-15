package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.entities.CurrencyEntity;
import br.edu.atitus.currencyapi.repositories.CurrencyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceJpaTest {

    private static final LocalDate QUOTE_DATE = LocalDate.of(2024, 1, 2);

    @Mock
    private PtaxQuoteService ptaxQuoteService;

    @Mock
    private CurrencyRepository repository;

    @Test
    void calculatesForeignCurrencyCrossRateThroughBrl() throws Exception {
        when(ptaxQuoteService.getLatestSellQuote("USD", QUOTE_DATE))
                .thenReturn(Optional.of(new BigDecimal("5.125000")));
        when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE))
                .thenReturn(Optional.of(new BigDecimal("5.562500")));
        var service = service();

        var response = service.findBySourceCurrencyAndTargetCurrency(" usd ", "eur");

        assertEquals("USD", response.sourceCurrency());
        assertEquals("EUR", response.targetCurrency());
        assertEquals(0.921348, response.conversionRate());
        verify(ptaxQuoteService).getLatestSellQuote("USD", QUOTE_DATE);
        verify(ptaxQuoteService).getLatestSellQuote("EUR", QUOTE_DATE);
    }

    @Test
    void usesOneForBrlAndIdentityConversionWithoutExternalCalls() throws Exception {
        when(ptaxQuoteService.getLatestSellQuote("USD", QUOTE_DATE))
            .thenReturn(Optional.of(new BigDecimal("5.125")));
        var service = service();

        var toBrl = service.findBySourceCurrencyAndTargetCurrency("USD", "BRL");
        var sameCurrency = service.findBySourceCurrencyAndTargetCurrency("brl", "BRL");

        assertEquals(5.125, toBrl.conversionRate());
        assertEquals(1.0, sameCurrency.conversionRate());
        verify(ptaxQuoteService).getLatestSellQuote("USD", QUOTE_DATE);
        verify(ptaxQuoteService, never()).getLatestSellQuote("BRL", QUOTE_DATE);
    }

    @Test
    void returnsNotFoundWhenRequiredExternalQuoteIsMissing() {
        when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE)).thenReturn(Optional.empty());
        var service = service();

        assertThrows(
                EntityNotFoundException.class,
                () -> service.findBySourceCurrencyAndTargetCurrency("EUR", "BRL")
        );
    }

            @Test
            void fallsBackToBothLocalLegsWhenAnExternalQuoteIsMissing() throws Exception {
            when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE))
                .thenReturn(Optional.of(new BigDecimal("5.50")));
            when(ptaxQuoteService.getLatestSellQuote("GBP", QUOTE_DATE)).thenReturn(Optional.empty());
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Optional.of(localRate("EUR", 0.87)));
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "GBP"))
                .thenReturn(Optional.of(localRate("GBP", 0.75)));
            var service = service();

            var response = service.findBySourceCurrencyAndTargetCurrency("EUR", "GBP");

            assertEquals(0.862069, response.conversionRate());
            verify(repository).findBySourceCurrencyAndTargetCurrency("USD", "EUR");
            verify(repository).findBySourceCurrencyAndTargetCurrency("USD", "GBP");
            }

            @Test
            void fallsBackToBothLocalLegsWhenExternalCommunicationFails() throws Exception {
            when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE))
                .thenThrow(new IllegalStateException("BCB unavailable"));
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Optional.of(localRate("EUR", 0.87)));
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "BRL"))
                .thenReturn(Optional.of(localRate("BRL", 5.15)));
            var service = service();

            var response = service.findBySourceCurrencyAndTargetCurrency("EUR", "BRL");

            assertEquals(5.919540, response.conversionRate());
            verify(repository).findBySourceCurrencyAndTargetCurrency("USD", "EUR");
            verify(repository).findBySourceCurrencyAndTargetCurrency("USD", "BRL");
            }

            @Test
            void returnsNotFoundWhenLocalFallbackDoesNotHaveBothLegs() {
            when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE)).thenReturn(Optional.empty());
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Optional.of(localRate("EUR", 0.87)));
            when(repository.findBySourceCurrencyAndTargetCurrency("USD", "GBP"))
                .thenReturn(Optional.empty());
            var service = service();

            assertThrows(
                EntityNotFoundException.class,
                () -> service.findBySourceCurrencyAndTargetCurrency("EUR", "GBP")
            );
            }

    @Test
    void roundsTheConversionRateToSixDecimalPlacesHalfUp() throws Exception {
        when(ptaxQuoteService.getLatestSellQuote("USD", QUOTE_DATE))
                .thenReturn(Optional.of(new BigDecimal("1.000000")));
        when(ptaxQuoteService.getLatestSellQuote("EUR", QUOTE_DATE))
            .thenReturn(Optional.of(new BigDecimal("6.666640")));
        var service = service();

        var response = service.findBySourceCurrencyAndTargetCurrency("USD", "EUR");

        assertEquals(0.150001, response.conversionRate());
    }

    private CurrencyServiceJpa service() {
        var service = new CurrencyServiceJpa(ptaxQuoteService, repository, QUOTE_DATE);
        ReflectionTestUtils.setField(service, "serverPort", "8100");
        return service;
    }

    private CurrencyEntity localRate(String target, double rate) {
        return new CurrencyEntity(1L, "USD", target, rate);
    }
}
