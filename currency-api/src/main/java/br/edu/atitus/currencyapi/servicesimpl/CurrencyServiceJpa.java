package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.dtos.CurrencyResponse;
import br.edu.atitus.currencyapi.services.CurrencyService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrencyServiceJpa implements CurrencyService {

    private static final BigDecimal BRL_QUOTE = BigDecimal.ONE;
    private static final int RATE_SCALE = 6;

    private final PtaxQuoteService ptaxQuoteService;
    private final LocalDate quoteDate;

    public CurrencyServiceJpa(
            PtaxQuoteService ptaxQuoteService,
            @Value("${app.currency.bcb.quote-date}") LocalDate quoteDate
    ) {
        this.ptaxQuoteService = ptaxQuoteService;
        this.quoteDate = quoteDate;
    }

    @Value("${server.port:8080}")
    private String serverPort;

    @Override
    public CurrencyResponse findBySourceCurrencyAndTargetCurrency(
            String sourceCurrency,
            String targetCurrency
    ) throws Exception {
        var normalizedSource = normalizeCurrency(sourceCurrency);
        var normalizedTarget = normalizeCurrency(targetCurrency);

        BigDecimal conversionRate;
        if (normalizedSource.equals(normalizedTarget)) {
            conversionRate = BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        } else {
            var sourceQuote = getQuoteInBrl(normalizedSource);
            var targetQuote = getQuoteInBrl(normalizedTarget);
            conversionRate = sourceQuote.divide(targetQuote, RATE_SCALE, RoundingMode.HALF_UP);
        }

        String environment = "Currency API running in Port: " + serverPort;
        return new CurrencyResponse(
                normalizedSource,
                normalizedTarget,
                conversionRate.doubleValue(),
                environment
        );
    }

    private BigDecimal getQuoteInBrl(String currency) {
        if ("BRL".equals(currency)) {
            return BRL_QUOTE;
        }

        return ptaxQuoteService.getLatestSellQuote(currency, quoteDate)
                .filter(quote -> quote.signum() > 0)
                .orElseThrow(() -> new EntityNotFoundException("Cotação não encontrada para " + currency));
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Código de moeda inválido");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}