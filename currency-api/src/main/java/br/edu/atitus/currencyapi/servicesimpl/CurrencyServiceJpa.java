package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.dtos.CurrencyResponse;
import br.edu.atitus.currencyapi.entities.CurrencyEntity;
import br.edu.atitus.currencyapi.repositories.CurrencyRepository;
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
    private static final String USD = "USD";
    private static final int RATE_SCALE = 6;

    private final PtaxQuoteService ptaxQuoteService;
    private final CurrencyRepository repository;
    private final LocalDate quoteDate;

    public CurrencyServiceJpa(
            PtaxQuoteService ptaxQuoteService,
            CurrencyRepository repository,
            @Value("${app.currency.bcb.quote-date}") LocalDate quoteDate
    ) {
        this.ptaxQuoteService = ptaxQuoteService;
        this.repository = repository;
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
            conversionRate = getExternalRate(normalizedSource, normalizedTarget)
                    .orElseGet(() -> getLocalRate(normalizedSource, normalizedTarget));
        }

        String environment = "Currency API running in Port: " + serverPort;
        return new CurrencyResponse(
                normalizedSource,
                normalizedTarget,
                conversionRate.doubleValue(),
                environment
        );
    }

    private java.util.Optional<BigDecimal> getExternalRate(String sourceCurrency, String targetCurrency) {
        try {
            var sourceQuote = getExternalQuoteInBrl(sourceCurrency);
            var targetQuote = getExternalQuoteInBrl(targetCurrency);
            if (sourceQuote.isEmpty() || targetQuote.isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(sourceQuote.orElseThrow()
                    .divide(targetQuote.orElseThrow(), RATE_SCALE, RoundingMode.HALF_UP));
        } catch (RuntimeException externalFailure) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<BigDecimal> getExternalQuoteInBrl(String currency) {
        if ("BRL".equals(currency)) {
            return java.util.Optional.of(BRL_QUOTE);
        }

        return ptaxQuoteService.getLatestSellQuote(currency, quoteDate)
                .filter(quote -> quote.signum() > 0);
    }

    private BigDecimal getLocalRate(String sourceCurrency, String targetCurrency) {
        var usdToSource = getUsdToCurrencyRate(sourceCurrency);
        var usdToTarget = getUsdToCurrencyRate(targetCurrency);
        return usdToTarget.divide(usdToSource, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal getUsdToCurrencyRate(String currency) {
        if (USD.equals(currency)) {
            return BigDecimal.ONE;
        }

        CurrencyEntity usdRate = repository.findBySourceCurrencyAndTargetCurrency(USD, currency)
                .orElseThrow(() -> new EntityNotFoundException("Cotação local não encontrada para " + currency));
        if (usdRate.getConversionRate() == null || usdRate.getConversionRate() <= 0) {
            throw new EntityNotFoundException("Cotação local inválida para " + currency);
        }
        return BigDecimal.valueOf(usdRate.getConversionRate());
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Código de moeda inválido");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}