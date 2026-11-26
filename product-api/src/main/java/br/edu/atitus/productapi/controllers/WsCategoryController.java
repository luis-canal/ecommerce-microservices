package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.CategoryRequest;
import br.edu.atitus.productapi.dtos.CategoryResponse;
import br.edu.atitus.productapi.dtos.CategoryStatusRequest;
import br.edu.atitus.productapi.dtos.SubcategoryResponse;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ws/categories")
public class WsCategoryController {

    private final CategoryService service;
    private final AuthorizationService authorizationService;

    public WsCategoryController(CategoryService service, AuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<Page<CategoryResponse>> getAll(
            @PageableDefault(size = 10, sort = "name", page = 0) Pageable pageable,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(service.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}/subcategories")
    public ResponseEntity<Page<SubcategoryResponse>> getSubcategories(
            @PathVariable Long id,
            @PageableDefault(size = 10, sort = "name", page = 0) Pageable pageable,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(service.findSubcategoriesForAdmin(id, pageable));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @RequestBody CategoryRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.status(201).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @RequestBody CategoryRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CategoryResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody CategoryStatusRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(service.updateStatus(id, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(exception.getReason() == null ? "Requisição não aceita." : exception.getReason());
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(jakarta.persistence.EntityNotFoundException exception) {
        return ResponseEntity.status(404)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body("Categoria não encontrada.");
    }
}
