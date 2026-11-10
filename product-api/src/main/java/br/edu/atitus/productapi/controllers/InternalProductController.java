package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.InventoryProductData;
import br.edu.atitus.productapi.dtos.CartProductData;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.servicesimpl.CurrencyClientService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductRepository productRepository;
    private final CurrencyClientService currencyClientService;
    private final String serviceToken;

    public InternalProductController(
            ProductRepository productRepository,
            CurrencyClientService currencyClientService,
            @Value("${inventory.security.service-token:}") String serviceToken
    ) {
        this.productRepository = productRepository;
        this.currencyClientService = currencyClientService;
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

    @GetMapping("/{productId}/cart-data")
    public ResponseEntity<CartProductData> getCartData(
            @PathVariable Long productId,
            @RequestHeader(name = "X-Service-Token", required = false) String suppliedToken
    ) {
        requireServiceToken(suppliedToken);
        if (productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId deve ser positivo.");
        }

        var product = productRepository.findCartDataById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
        BigDecimal convertedPrice = product.getPrice();
        if (!"BRL".equalsIgnoreCase(product.getCurrency())) {
            if (product.getCurrency() == null || product.getCurrency().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O produto não possui uma moeda válida.");
            }
            var quote = currencyClientService.getCurrency(product.getCurrency(), "BRL");
            var rate = quote == null ? null : quote.conversionRate();
            if (rate == null || !Double.isFinite(rate) || rate <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A conversão do preço para BRL falhou.");
            }
            convertedPrice = convertedPrice.multiply(BigDecimal.valueOf(rate))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return ResponseEntity.ok(new CartProductData(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getModel(),
                product.getImage(),
                product.getCurrency(),
                product.getPrice(),
                convertedPrice,
                product.getProductType(),
                product.isActive()
                        && product.getSubcategory().isActive()
                        && product.getSubcategory().getCategory().isActive()
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
