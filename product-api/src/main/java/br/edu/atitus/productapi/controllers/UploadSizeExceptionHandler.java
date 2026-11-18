package br.edu.atitus.productapi.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * O limite de multipart do Spring é verificado antes de o controller ser escolhido,
 * então o tratamento precisa ser global para responder 413 em vez de 500.
 */
@RestControllerAdvice
public class UploadSizeExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("A imagem excede o tamanho máximo permitido.");
    }
}
