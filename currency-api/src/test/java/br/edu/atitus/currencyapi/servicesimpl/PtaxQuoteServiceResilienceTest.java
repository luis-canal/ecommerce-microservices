package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.clients.PtaxClient;
import br.edu.atitus.currencyapi.dtos.PtaxQuote;
import br.edu.atitus.currencyapi.dtos.PtaxResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = PtaxQuoteServiceResilienceTest.TestApplication.class,
        properties = {
                "spring.cache.type=caffeine",
                "spring.cache.cache-names=ptaxQuotes",
                "spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m",
                "spring.cloud.consul.config.enabled=false",
                "spring.cloud.consul.enabled=false",
                "resilience4j.retry.instances.bcb-ptax.max-attempts=3",
                "resilience4j.retry.instances.bcb-ptax.wait-duration=1ms",
                "resilience4j.circuitbreaker.instances.bcb-ptax.sliding-window-size=100",
                "resilience4j.circuitbreaker.instances.bcb-ptax.minimum-number-of-calls=100"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PtaxQuoteServiceResilienceTest {

    @Autowired
    private PtaxQuoteService service;

    @Autowired
    private CacheManager cacheManager;

        @Autowired
        private CircuitBreakerRegistry circuitBreakerRegistry;

        @Autowired
        private RetryRegistry retryRegistry;

    @MockitoBean
    private PtaxClient ptaxClient;

    @BeforeEach
    void clearCacheAndMock() {
        cacheManager.getCache("ptaxQuotes").clear();
        reset(ptaxClient);
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> circuitBreaker.reset());
    }

    @Test
    void cachesValidQuotesByNormalizedCurrencyAndDate() {
        var date = LocalDate.of(2024, 1, 2);
        when(ptaxClient.getQuotes("USD", "01-02-2024"))
                .thenReturn(new PtaxResponse(List.of(quote("5.10"))));

        var first = service.getLatestSellQuote("usd", date);
        var second = service.getLatestSellQuote("USD", date);

        assertEquals(new BigDecimal("5.10"), first.orElseThrow());
        assertEquals(first, second);
        verify(ptaxClient).getQuotes("USD", "01-02-2024");
    }

    @Test
    void doesNotCacheEmptyResultsOrFailedCallsAndRetriesFailuresThreeTimes() {
        var date = LocalDate.of(2024, 1, 2);
        when(ptaxClient.getQuotes("EUR", "01-02-2024"))
                .thenReturn(new PtaxResponse(List.of()), new PtaxResponse(List.of()))
                .thenThrow(new RestClientException("temporary upstream failure"));

        assertTrue(service.getLatestSellQuote("EUR", date).isEmpty());
        assertTrue(service.getLatestSellQuote("EUR", date).isEmpty());
        verify(ptaxClient, org.mockito.Mockito.times(2)).getQuotes("EUR", "01-02-2024");

        org.junit.jupiter.api.Assertions.assertThrows(
                RestClientException.class,
                () -> service.getLatestSellQuote("EUR", date)
        );
        verify(ptaxClient, org.mockito.Mockito.times(5)).getQuotes("EUR", "01-02-2024");
    }

    @Test
    void retriesExternalFailuresThreeTimes() {
        var date = LocalDate.of(2024, 1, 2);
        when(ptaxClient.getQuotes("EUR", "01-02-2024"))
                .thenThrow(new RestClientException("temporary upstream failure"));

        org.junit.jupiter.api.Assertions.assertThrows(
                RestClientException.class,
                () -> service.getLatestSellQuote("EUR", date)
        );

        verify(ptaxClient, org.mockito.Mockito.times(3)).getQuotes("EUR", "01-02-2024");
    }

    @Test
    void doesNotRetryWhenCircuitBreakerRejectsCall() {
        var date = LocalDate.of(2024, 1, 2);
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("bcb-ptax");
        circuitBreaker.transitionToOpenState();
        var retry = retryRegistry.retry("bcb-ptax");
        var retryEvents = new AtomicInteger();
        retry.getEventPublisher().onRetry(event -> retryEvents.incrementAndGet());

        org.junit.jupiter.api.Assertions.assertThrows(
                CallNotPermittedException.class,
                () -> service.getLatestSellQuote("GBP", date)
        );

        verify(ptaxClient, org.mockito.Mockito.never()).getQuotes("GBP", "01-02-2024");
        assertEquals(0, retryEvents.get());
    }

    private PtaxQuote quote(String sell) {
        return new PtaxQuote(
                new BigDecimal("5.00"),
                new BigDecimal(sell),
                "2024-01-02 16:00:00.000",
                "Fechamento"
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @EnableCaching
    @Import(PtaxQuoteService.class)
        static class TestApplication {
        }
}
