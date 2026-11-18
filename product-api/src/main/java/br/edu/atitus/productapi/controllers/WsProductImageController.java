package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductImageResponse;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.ProductImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/ws/products/{productId}/images")
public class WsProductImageController {

    private final ProductImageService imageService;
    private final AuthorizationService authService;

    public WsProductImageController(ProductImageService imageService, AuthorizationService authService) {
        this.imageService = imageService;
        this.authService = authService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(
            @PathVariable Long productId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        return ResponseEntity.status(201).body(imageService.upload(productId, file));
    }

    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> list(
            @PathVariable Long productId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        return ResponseEntity.ok(imageService.list(productId));
    }

    @PatchMapping("/{imageId}/primary")
    public ResponseEntity<ProductImageResponse> setPrimary(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        return ResponseEntity.ok(imageService.setPrimary(productId, imageId));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        imageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<String> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body("Arquivo de imagem obrigatório no campo \"file\".");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception ex) {
        return ResponseEntity.status(500).body("Ops!!! Algo deu errado. - " + ex.getMessage());
    }
}
