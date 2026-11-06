package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.InventoryProductData;
import br.edu.atitus.productapi.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductRepository productRepository;
    private final String serviceToken;

    public InternalProductController(
            ProductRepository productRepository,
            @Value("${inventory.security.service-token:}") String serviceToken
    ) {
        this.productRepository = productRepository;
        this.serviceToken = serviceToken;
    }

    @GetMapping("/{productId}/inventory-data")
    public ResponseEntity<InventoryProductData> getInventoryData(
            @PathVariable Long productId,
            @RequestHeader(name = "X-Service-Token", required = false) String suppliedToken
    ) {
        requireServiceToken(suppliedToken);
        if (productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId deve ser positivo.");
        }

        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
        return ResponseEntity.ok(new InventoryProductData(
                product.getId(),
                product.getProductType(),
                product.isActive()
        ));
    }

    private void requireServiceToken(String suppliedToken) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A autenticação serviço-a-serviço não está configurada."
            );
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de serviço inválida.");
        }
    }
}
