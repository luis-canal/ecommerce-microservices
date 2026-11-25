package br.edu.atitus.productapi.controllers;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatus(ResponseStatusException exception) {
        return text(exception.getStatusCode(), nonBlank(exception.getReason(), "Requisição não aceita."));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EntityNotFoundException exception) {
        return text(HttpStatus.NOT_FOUND, nonBlank(exception.getMessage(), "Produto não encontrado."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidArgument(IllegalArgumentException exception) {
        return text(HttpStatus.BAD_REQUEST, "Parâmetro ou campo inválido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return text(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + exception.getName() + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParameter(MissingServletRequestParameterException exception) {
        return text(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + exception.getParameterName() + ".");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<String> handleMissingHeader(MissingRequestHeaderException exception) {
        return text(HttpStatus.BAD_REQUEST, "Cabeçalho obrigatório ausente: " + exception.getHeaderName() + ".");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<String> handleMissingPart(MissingServletRequestPartException exception) {
        return text(HttpStatus.BAD_REQUEST, "Parte obrigatória ausente: " + exception.getRequestPartName() + ".");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return text(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception exception) {
        log.error("Erro inesperado ao processar requisição.", exception);
        return text(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno. Tente novamente mais tarde.");
    }

    private ResponseEntity<String> text(HttpStatusCode status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(message);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
