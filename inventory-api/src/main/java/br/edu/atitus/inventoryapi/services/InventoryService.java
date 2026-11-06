package br.edu.atitus.inventoryapi.services;

import br.edu.atitus.inventoryapi.dtos.AvailabilityBatchRequest;
import br.edu.atitus.inventoryapi.dtos.InventoryAdjustmentRequest;
import br.edu.atitus.inventoryapi.dtos.InventoryMovementResponse;
import br.edu.atitus.inventoryapi.dtos.InventoryResponse;
import br.edu.atitus.inventoryapi.dtos.InventoryAnalyticsSummary;
import br.edu.atitus.inventoryapi.dtos.LowStockThresholdRequest;
import br.edu.atitus.inventoryapi.dtos.StockMovementRequest;
import br.edu.atitus.inventoryapi.clients.ProductApiClient;
import br.edu.atitus.inventoryapi.entities.InventoryEntity;
import br.edu.atitus.inventoryapi.entities.InventoryMovementEntity;
import br.edu.atitus.inventoryapi.enums.MovementType;
import br.edu.atitus.inventoryapi.enums.ProductType;
import br.edu.atitus.inventoryapi.repositories.InventoryMovementRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductApiClient productApiClient;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ProductApiClient productApiClient
    ) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.productApiClient = productApiClient;
    }

    @Transactional
    public InventoryResponse registerProduct(Long productId) {
        validateProductId(productId);
        ProductType productType = productApiClient.getProductType(productId);
        if (productType == null) {
            throw badRequest("O tipo de produto é obrigatório.");
        }

        InventoryEntity inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> inventoryRepository.save(new InventoryEntity(productId, productType)));
        if (inventory.getProductType() != productType) {
            throw conflict("O tipo de produto informado diverge do tipo registrado no estoque.");
        }
        return InventoryResponse.from(inventory);
    }

    public InventoryResponse getInventory(Long productId) {
        return InventoryResponse.from(findInventory(productId));
    }

    public Page<InventoryResponse> search(
            ProductType productType,
            Boolean lowStock,
            Pageable pageable
    ) {
        return inventoryRepository.search(productType, lowStock, pageable)
                .map(InventoryResponse::from);
    }

    public InventoryAnalyticsSummary getAnalyticsSummary() {
        var summary = inventoryRepository.getSummary();
        return new InventoryAnalyticsSummary(
                summary.getInventoryCount(),
                summary.getQuantityOnHand(),
                summary.getQuantityReserved(),
                summary.getAvailableQuantity(),
                summary.getOutOfStockCount(),
                summary.getLowStockCount()
        );
    }

    public Page<InventoryResponse> getOutOfStock(Pageable pageable) {
        return inventoryRepository.findOutOfStock(pageable).map(InventoryResponse::from);
    }

    public Page<InventoryResponse> getLowStock(Pageable pageable) {
        return inventoryRepository.search(null, true, pageable).map(InventoryResponse::from);
    }

    public InventoryResponse getAvailability(Long productId) {
        return getInventory(productId);
    }

    public List<InventoryResponse> getAvailability(AvailabilityBatchRequest request) {
        if (request == null || request.productIds() == null || request.productIds().isEmpty()) {
            throw badRequest("Informe ao menos um productId.");
        }
        if (request.productIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw badRequest("Todos os productIds devem ser positivos.");
        }
        if (request.productIds().size() > 500) {
            throw badRequest("A consulta em lote aceita no máximo 500 produtos.");
        }
        if (request.productIds().stream().distinct().count() != request.productIds().size()) {
            throw badRequest("A lista de productIds não pode conter duplicados.");
        }

        List<InventoryEntity> inventory = inventoryRepository.findAllByProductIdIn(request.productIds());
        Map<Long, InventoryEntity> byProductId = new HashMap<>();
        inventory.forEach(item -> byProductId.put(item.getProductId(), item));

        List<Long> missing = request.productIds().stream()
                .filter(id -> !byProductId.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estoque não encontrado para: " + missing);
        }

        return request.productIds().stream()
                .map(byProductId::get)
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional
    public InventoryResponse registerEntry(Long productId, StockMovementRequest request) {
        validateMovementRequest(request);
        InventoryEntity inventory = findInventoryForUpdate(productId);
        long newQuantity = addExact(inventory.getQuantityOnHand(), request.quantity());
        validatePhysicalQuantity(inventory, newQuantity);
        inventory.addToPhysicalQuantity(request.quantity());
        recordMovement(inventory, MovementType.ENTRY, request.quantity(), request.reason());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse registerManualExit(Long productId, StockMovementRequest request) {
        validateMovementRequest(request);
        InventoryEntity inventory = findInventoryForUpdate(productId);
        if (request.quantity() > inventory.getAvailableQuantity()) {
            throw conflict("Saída excede a quantidade disponível.");
        }
        inventory.addToPhysicalQuantity(-request.quantity());
        recordMovement(inventory, MovementType.MANUAL_EXIT, -request.quantity(), request.reason());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse adjust(Long productId, InventoryAdjustmentRequest request) {
        if (request == null) {
            throw badRequest("Informe a quantidade e o motivo do ajuste.");
        }
        validateReason(request.reason());
        if (request.newQuantity() < 0) {
            throw badRequest("A quantidade física não pode ser negativa.");
        }

        InventoryEntity inventory = findInventoryForUpdate(productId);
        if (request.newQuantity() < inventory.getQuantityReserved()) {
            throw conflict("O ajuste não pode reduzir o estoque abaixo da quantidade reservada.");
        }
        validatePhysicalQuantity(inventory, request.newQuantity());

        long delta = request.newQuantity() - inventory.getQuantityOnHand();
        if (delta == 0) {
            throw badRequest("O ajuste deve alterar a quantidade física.");
        }
        inventory.addToPhysicalQuantity(delta);
        recordMovement(inventory, MovementType.ADJUSTMENT, delta, request.reason());
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse setLowStockThreshold(Long productId, LowStockThresholdRequest request) {
        if (request == null || request.threshold() < 0) {
            throw badRequest("O limite de estoque baixo deve ser maior ou igual a zero.");
        }
        InventoryEntity inventory = findInventoryForUpdate(productId);
        inventory.setLowStockThreshold(request.threshold());
        return InventoryResponse.from(inventory);
    }

    public Page<InventoryMovementResponse> getProductMovements(
            Long productId,
            MovementType movementType,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        findInventory(productId);
        validateDateRange(from, to);
        return movementRepository.findAll(movementSpecification(productId, movementType, from, to), pageable)
                .map(InventoryMovementResponse::from);
    }

    public Page<InventoryMovementResponse> searchMovements(
            Long productId,
            MovementType movementType,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        validateDateRange(from, to);
        if (productId != null && productId <= 0) {
            throw badRequest("productId deve ser positivo.");
        }
        if (productId != null && !inventoryRepository.existsByProductId(productId)) {
            throw notFound(productId);
        }
        return movementRepository.findAll(movementSpecification(productId, movementType, from, to), pageable)
                .map(InventoryMovementResponse::from);
    }

    private Specification<InventoryMovementEntity> movementSpecification(
            Long productId,
            MovementType movementType,
            Instant from,
            Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("inventory").get("productId"),
                        productId
                ));
            }
            if (movementType != null) {
                predicates.add(criteriaBuilder.equal(root.get("movementType"), movementType));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private InventoryEntity findInventory(Long productId) {
        validateProductId(productId);
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> notFound(productId));
    }

    private InventoryEntity findInventoryForUpdate(Long productId) {
        validateProductId(productId);
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> notFound(productId));
    }

    private void validatePhysicalQuantity(InventoryEntity inventory, long quantity) {
        if (quantity < 0) {
            throw badRequest("A quantidade física não pode ser negativa.");
        }
        if (inventory.getProductType() == ProductType.SINGLE_UNIT && quantity > 1) {
            throw conflict("Produtos SINGLE_UNIT podem ter no máximo uma unidade física.");
        }
    }

    private void recordMovement(
            InventoryEntity inventory,
            MovementType movementType,
            long quantityDelta,
            String reason
    ) {
        movementRepository.save(new InventoryMovementEntity(inventory, movementType, quantityDelta, reason));
    }

    private void validateMovementRequest(StockMovementRequest request) {
        if (request == null || request.quantity() <= 0) {
            throw badRequest("A quantidade deve ser maior que zero.");
        }
        validateReason(request.reason());
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw badRequest("O motivo é obrigatório e deve ter no máximo 500 caracteres.");
        }
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw badRequest("O início do período deve ser anterior ao fim.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw badRequest("productId deve ser positivo.");
        }
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw badRequest("A quantidade informada excede o limite suportado.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(Long productId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Estoque não encontrado para o produto " + productId + ".");
    }
}
