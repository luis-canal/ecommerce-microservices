package br.edu.atitus.analyticsapi.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Executa chamadas aos serviços de origem com retentativas e backoff exponencial.
 * Erros 4xx (exceto 408/429) não são repetidos, pois indicam problema de contrato ou credencial.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final int maxAttempts;
    private final long backoffMs;

    public RetryExecutor(
            @Value("${analytics.collection.max-attempts:3}") int maxAttempts,
            @Value("${analytics.collection.retry-backoff-ms:1000}") long backoffMs
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
    }

    public <T> T execute(String operation, Supplier<T> call) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (!isRetryable(exception) || attempt == maxAttempts) {
                    break;
                }
                long wait = backoffMs * (1L << (attempt - 1));
                log.warn("Falha em {} (tentativa {}/{}): {}. Nova tentativa em {} ms.",
                        operation, attempt, maxAttempts, exception.getMessage(), wait);
                sleep(wait);
            }
        }
        throw new IllegalStateException("Falha ao executar " + operation + ": "
                + (lastFailure == null ? "erro desconhecido" : lastFailure.getMessage()), lastFailure);
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status >= 500
                    || status == HttpStatus.TOO_MANY_REQUESTS.value()
                    || status == HttpStatus.REQUEST_TIMEOUT.value();
        }
        return true;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coleta interrompida.", interrupted);
        }
    }
}
