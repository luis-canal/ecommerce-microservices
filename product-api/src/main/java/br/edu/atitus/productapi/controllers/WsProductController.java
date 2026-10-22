package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.services.ProductService;
import br.edu.atitus.commons.web.RequireRole;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
// Rota legada mantida por contrato nesta fase; a migração para /ws/admin/products fica para P2.
@RequestMapping("/ws/products")
public class WsProductController {

    private final ProductService service;
    public WsProductController(
            @Qualifier("productServiceJpa") ProductService service
    ) {
        this.service = service;
    }

    @PostMapping
        @RequireRole("ADMINISTRADOR")
    public ResponseEntity<ProductEntity> postProduct(
            @RequestBody ProductRequest dto
    ) throws Exception {
        var product = service.save(dto);
        return ResponseEntity.status(201).body(product);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(e.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception ex) {
        return ResponseEntity.status(500)
                .body("Ops!!! Algo deu errado. - " + ex.getMessage());
    }
}
