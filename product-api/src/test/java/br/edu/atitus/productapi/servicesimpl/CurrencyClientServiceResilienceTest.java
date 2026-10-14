package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.clients.CurrencyClient;
import br.edu.atitus.productapi.dtos.CurrencyResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(
        classes = CurrencyClientServiceResilienceTest.TestApplication.class,
        properties = {
                "spring.config.import=",
                "spring.main.web-application-type=none",
                "spring.cache.type=caffeine",
                "spring.cache.cache-names=currency-api",
                "spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=10m",
                "resilience4j.retry.instances.currency-api.max-attempts=3",
                "resilience4j.retry.instances.currency-api.wait-duration=5ms",
                "resilience4j.retry.instances.currency-api.ignore-exceptions[0]=io.github.resilience4j.circuitbreaker.CallNotPermittedException",
                "resilience4j.retry.retry-aspect-order=100",
                "resilience4j.circuitbreaker.instances.currency-api.sliding-window-type=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.currency-api.sliding-window-size=5",
                "resilience4j.circuitbreaker.instances.currency-api.minimum-number-of-calls=5",
                "resilience4j.circuitbreaker.instances.currency-api.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.currency-api.wait-duration-in-open-state=30s",
                "resilience4j.circuitbreaker.circuit-breaker-aspect-order=101"
        }
)
class CurrencyClientServiceResilienceTest {

    @Autowired
    private CurrencyClientService service;

    @Autowired
    private CurrencyClient currencyClient;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        cacheManager.getCache("currency-api").clear();
        circuitBreakerRegistry.circuitBreaker("currency-api").reset();
        reset(currencyClient);
    }

    @Test
    void cachesSuccessfulResultPerCurrencyPair() {
        var usdToBrl = new CurrencyResponse("USD", "BRL", 5.0, "cached");
        var eurToBrl = new CurrencyResponse("EUR", "BRL", 6.0, "cached");
        when(currencyClient.getCurrency("USD", "BRL")).thenReturn(usdToBrl);
        when(currencyClient.getCurrency("EUR", "BRL")).thenReturn(eurToBrl);

        assertEquals(usdToBrl, service.getCurrency("USD", "BRL"));
        assertEquals(usdToBrl, service.getCurrency("USD", "BRL"));
        assertEquals(eurToBrl, service.getCurrency("EUR", "BRL"));

        verify(currencyClient, times(1)).getCurrency("USD", "BRL");
        verify(currencyClient, times(1)).getCurrency("EUR", "BRL");
    }

    @Test
    void retriesTransientFailureUpToConfiguredAttemptLimit() {
        var response = new CurrencyResponse("USD", "BRL", 5.0, "recovered");
        when(currencyClient.getCurrency("USD", "BRL"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(response);

        assertEquals(response, service.getCurrency("USD", "BRL"));

        verify(currencyClient, times(3)).getCurrency("USD", "BRL");
    }

    @Test
    void opensCircuitAfterFailureWindowAndRejectsFurtherCalls() {
        when(currencyClient.getCurrency("USD", "BRL"))
                .thenThrow(new IllegalStateException("currency service down"));

        assertThrows(RuntimeException.class, () -> service.getCurrency("USD", "BRL"));
        assertThrows(RuntimeException.class, () -> service.getCurrency("USD", "BRL"));
        assertEquals("OPEN", circuitBreakerRegistry.circuitBreaker("currency-api").getState().name());
        verify(currencyClient, times(5)).getCurrency("USD", "BRL");

        assertThrows(CallNotPermittedException.class, () -> service.getCurrency("USD", "BRL"));
        verify(currencyClient, times(5)).getCurrency("USD", "BRL");
    }

    @Test
    void doesNotCacheFailureAndCanCacheLaterSuccess() {
        when(currencyClient.getCurrency("USD", "BRL"))
                .thenThrow(new IllegalStateException("currency service down"));

        assertThrows(RuntimeException.class, () -> service.getCurrency("USD", "BRL"));
        verify(currencyClient, times(3)).getCurrency("USD", "BRL");

        circuitBreakerRegistry.circuitBreaker("currency-api").reset();
        var response = new CurrencyResponse("USD", "BRL", 5.0, "recovered");
        doReturn(response).when(currencyClient).getCurrency("USD", "BRL");

        assertEquals(response, service.getCurrency("USD", "BRL"));
        assertEquals(response, service.getCurrency("USD", "BRL"));
        verify(currencyClient, times(4)).getCurrency("USD", "BRL");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableCaching(order = 99)
    @Import(CurrencyClientService.class)
    static class TestApplication {
        @Bean
        CurrencyClient currencyClient() {
            return mock(CurrencyClient.class);
        }
    }
}
