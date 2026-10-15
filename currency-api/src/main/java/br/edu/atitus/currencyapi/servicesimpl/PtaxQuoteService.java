package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.clients.PtaxClient;
import br.edu.atitus.currencyapi.dtos.PtaxQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class PtaxQuoteService {

    private static final DateTimeFormatter BCB_DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.ROOT);

    private final PtaxClient ptaxClient;

    public PtaxQuoteService(PtaxClient ptaxClient) {
        this.ptaxClient = ptaxClient;
    }

    @Cacheable(
            cacheNames = "ptaxQuotes",
            key = "#currency.toUpperCase(T(java.util.Locale).ROOT) + ':' + #date",
            unless = "#result == null"
    )
    @Retry(name = "bcb-ptax")
    @CircuitBreaker(name = "bcb-ptax")
    public Optional<BigDecimal> getLatestSellQuote(String currency, LocalDate date) {
        var response = ptaxClient.getQuotes(
                currency.toUpperCase(Locale.ROOT),
                date.format(BCB_DATE_FORMAT)
        );

        if (response == null || response.value() == null) {
            return Optional.empty();
        }

        return response.value().stream()
                .filter(quote -> quote != null
                        && quote.cotacaoVenda() != null
                        && quote.cotacaoVenda().signum() > 0
                        && quote.dataHoraCotacao() != null)
                .map(quote -> new DatedQuote(quote, parseDateTime(quote.dataHoraCotacao())))
                .filter(datedQuote -> datedQuote.dateTime() != null
                        && datedQuote.dateTime().toLocalDate().equals(date))
                .max(Comparator.comparing(DatedQuote::dateTime))
                .map(datedQuote -> datedQuote.quote().cotacaoVenda());
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record DatedQuote(PtaxQuote quote, LocalDateTime dateTime) {
    }
}
