package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.dtos.ProductWithImages;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.enums.ProductStatusFilter;
import br.edu.atitus.productapi.services.ProductService;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.ProductImageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

@RestController
@RequestMapping("/ws/products")
public class WsProductController {

    private final ProductService service;
    private final AuthorizationService authService;
    private final ProductImageService imageService;
    private final ObjectMapper objectMapper;

    public WsProductController(
            @Qualifier("productServiceJpa") ProductService service,
            AuthorizationService authService,
            ProductImageService imageService,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.authService = authService;
        this.imageService = imageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Listagem administrativa: mesmos filtros, paginação e ordenação de GET /products,
     * com o filtro adicional de status (padrão ACTIVE = mesmo resultado do catálogo público).
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAll(
            @RequestParam(defaultValue = "BRL") String targetCurrency,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "ACTIVE") ProductStatusFilter status,
            @PageableDefault(size = 10, sort = "description", page = 0) Pageable pageable,
            @RequestHeader("X-User-Type") Integer userType
    ) throws Exception {
        authService.requireAdmin(userType);
        var criteria = new ProductSearchCriteria(name, categoryId, subcategoryId, minPrice, maxPrice, status);
        String responseCurrency = targetCurrency == null || targetCurrency.isBlank() ? "BRL" : targetCurrency;
        return ResponseEntity.ok(service.findAll(criteria, pageable, responseCurrency));
    }

    /** Detalhe administrativo: retorna também produtos inativos. */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "BRL") String targetCurrency,
            @RequestHeader("X-User-Type") Integer userType
    ) throws Exception {
        authService.requireAdmin(userType);
        String responseCurrency = targetCurrency == null || targetCurrency.isBlank() ? "BRL" : targetCurrency;
        return ResponseEntity.ok(service.findByIdForAdmin(id, responseCurrency));
    }

    @PostMapping
    public ResponseEntity<ObjectNode> postProduct(
            @RequestBody ProductRequest dto,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Type") Integer userType
    ) throws Exception {
        // Valida se o usuário é administrador.
        authService.requireAdmin(userType);

        var product = service.save(dto);
        return ResponseEntity.status(201).body(toJson(product.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObjectNode> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductUpdateRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        var product = service.update(id, request);
        return ResponseEntity.ok(toJson(product.getId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ObjectNode> updateProductStatus(
            @PathVariable Long id,
            @RequestBody ProductStatusRequest request,
            @RequestHeader("X-User-Type") Integer userType
    ) {
        authService.requireAdmin(userType);
        var product = service.updateStatus(id, request);
        return ResponseEntity.ok(toJson(product.getId()));
    }

    /**
     * Mantém exatamente o formato antigo (entity com subcategory aninhado) e apenas
     * acrescenta os campos de imagem: image passa a ser resolvido e images é adicionado.
     */
    private ObjectNode toJson(Long productId) {
        ProductWithImages data = imageService.findProductWithImages(productId);
        ObjectNode json = objectMapper.valueToTree(data.product());
        json.put("image", data.image());
        json.set("images", objectMapper.valueToTree(data.images()));
        return json;
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

    /**
     * Parâmetro com tipo inválido (ex.: status=FOO) nas leituras administrativas responde 400.
     * As demais rotas deste controller mantêm o tratamento anterior (handler genérico).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> invalidParameterExceptionHandler(MethodArgumentTypeMismatchException exception) {
        if (!exception.getParameter().hasMethodAnnotation(GetMapping.class)) {
            return exceptionHandler(exception);
        }
        return ResponseEntity.badRequest().body("Parâmetro inválido: " + exception.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception ex) {
        return ResponseEntity.status(500)
                .body("Ops!!! Algo deu errado. - " + ex.getMessage());
    }
}
