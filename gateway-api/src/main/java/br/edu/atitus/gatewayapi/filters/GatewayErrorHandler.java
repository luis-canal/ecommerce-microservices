package br.edu.atitus.gatewayapi.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        HttpStatusCode status = statusFor(exception);
        String message = messageFor(status);
        if (status.is5xxServerError()) {
            log.error("Falha inesperada no API Gateway.", exception);
        }

        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                MediaType.parseMediaType("text/plain;charset=UTF-8"));
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private HttpStatusCode statusFor(Throwable exception) {
        if (isUnavailable(exception)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (exception instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String messageFor(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return "Requisição inválida.";
        }
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return "Token ausente, inválido ou revogado.";
        }
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return "Usuário sem permissão para esta operação.";
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "Recurso não encontrado.";
        }
        if (status.isSameCodeAs(HttpStatus.CONFLICT)) {
            return "A operação não pode ser concluída devido ao estado atual do recurso.";
        }
        if (status.is5xxServerError()) {
            return "Ocorreu um erro interno. Tente novamente mais tarde.";
        }
        return "A requisição não pode ser processada.";
    }

    private boolean isUnavailable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
