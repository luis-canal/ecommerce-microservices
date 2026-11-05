package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.services.ProductService;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ws/products")
public class WsProductController {

    private final ProductService service;
    private final AuthorizationService authService;

    public WsProductController(
            @Qualifier("productServiceJpa") ProductService service,
            AuthorizationService authService
    ) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ProductEntity> postProduct(
            @RequestBody ProductRequest dto,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Type") Integer userType
    ) throws Exception {
        // Valida se o usuário é administrador.
        authService.requireAdmin(userType);

        var product = service.save(dto);
        return ResponseEntity.status(201).body(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductEntity> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductUpdateRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductEntity> updateProductStatus(
            @PathVariable Long id,
            @RequestBody ProductStatusRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        return ResponseEntity.ok(service.updateStatus(id, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(e.getReason());
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(jakarta.persistence.EntityNotFoundException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception ex) {
        return ResponseEntity.status(500)
                .body("Ops!!! Algo deu errado. - " + ex.getMessage());
    }
}
