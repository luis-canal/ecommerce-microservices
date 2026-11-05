package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.services.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("products")
public class ProductController {
    private final ProductService service;

    public ProductController(@Qualifier("productServiceJpa") ProductService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(
            @PathVariable Long id,
            @RequestParam String targetCurrency
    ) throws Exception {
        var response = service.findById(id, targetCurrency);
        return ResponseEntity.ok(response);
    }


    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAll(
            @RequestParam(defaultValue = "BRL") String targetCurrency,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "description", page = 0) Pageable pageable
    ) throws Exception {
        var criteria = new ProductSearchCriteria(name, categoryId, subcategoryId, minPrice, maxPrice);
        String responseCurrency = targetCurrency == null || targetCurrency.isBlank() ? "BRL" : targetCurrency;
        var response = service.findAll(criteria, pageable, responseCurrency);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> responseStatusExceptionHandler(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> invalidParameterExceptionHandler(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body("Parâmetro inválido: " + exception.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidArgumentExceptionHandler(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> entityNotFoundExceptionHandler(EntityNotFoundException ex){
        return ResponseEntity.status(404).body(ex.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception ex){
        return ResponseEntity.status(500).body("Ops!!! Algo deu errado. - " + ex.getMessage());
    }
}