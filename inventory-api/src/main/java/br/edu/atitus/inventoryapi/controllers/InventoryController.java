package br.edu.atitus.inventoryapi.controllers;

import br.edu.atitus.inventoryapi.dtos.AvailabilityBatchRequest;
import br.edu.atitus.inventoryapi.dtos.InventoryAdjustmentRequest;
import br.edu.atitus.inventoryapi.dtos.InventoryMovementResponse;
import br.edu.atitus.inventoryapi.dtos.InventoryResponse;
import br.edu.atitus.inventoryapi.dtos.LowStockThresholdRequest;
import br.edu.atitus.inventoryapi.dtos.StockMovementRequest;
import br.edu.atitus.inventoryapi.enums.MovementType;
import br.edu.atitus.inventoryapi.enums.ProductType;
import br.edu.atitus.inventoryapi.services.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/ws/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getInventory(productId));
    }

    @GetMapping
    public ResponseEntity<Page<InventoryResponse>> list(
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) Boolean lowStock,
            @PageableDefault(size = 20, sort = "productId") Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryService.search(productType, lowStock, pageable));
    }

    @GetMapping("/{productId}/availability")
    public ResponseEntity<InventoryResponse> getAvailability(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getAvailability(productId));
    }

    @PostMapping("/availability/batch")
    public ResponseEntity<List<InventoryResponse>> getAvailabilityBatch(
            @RequestBody AvailabilityBatchRequest request
    ) {
        return ResponseEntity.ok(inventoryService.getAvailability(request));
    }

    @PostMapping("/{productId}/entries")
    public ResponseEntity<InventoryResponse> registerEntry(
            @PathVariable Long productId,
            @RequestBody StockMovementRequest request
    ) {
        return ResponseEntity.ok(inventoryService.registerEntry(productId, request));
    }

    @PostMapping("/{productId}/exits")
    public ResponseEntity<InventoryResponse> registerManualExit(
            @PathVariable Long productId,
            @RequestBody StockMovementRequest request
    ) {
        return ResponseEntity.ok(inventoryService.registerManualExit(productId, request));
    }

    @PostMapping("/{productId}/adjustments")
    public ResponseEntity<InventoryResponse> adjust(
            @PathVariable Long productId,
            @RequestBody InventoryAdjustmentRequest request
    ) {
        return ResponseEntity.ok(inventoryService.adjust(productId, request));
    }

    @PatchMapping("/{productId}/low-stock-threshold")
    public ResponseEntity<InventoryResponse> setLowStockThreshold(
            @PathVariable Long productId,
            @RequestBody LowStockThresholdRequest request
    ) {
        return ResponseEntity.ok(inventoryService.setLowStockThreshold(productId, request));
    }

    @GetMapping("/{productId}/movements")
    public ResponseEntity<Page<InventoryMovementResponse>> productMovements(
            @PathVariable Long productId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                inventoryService.getProductMovements(productId, movementType, from, to, pageable)
        );
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<InventoryMovementResponse>> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                inventoryService.searchMovements(productId, movementType, from, to, pageable)
        );
    }
}
