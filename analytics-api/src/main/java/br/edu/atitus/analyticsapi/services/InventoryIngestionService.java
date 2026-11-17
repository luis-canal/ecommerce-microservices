package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.SourceInventory;
import br.edu.atitus.analyticsapi.entities.InventoryMovementEntity;
import br.edu.atitus.analyticsapi.entities.InventoryProductStatusEntity;
import br.edu.atitus.analyticsapi.entities.InventorySnapshotEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import br.edu.atitus.analyticsapi.repositories.InventoryMovementRepository;
import br.edu.atitus.analyticsapi.repositories.InventoryProductStatusRepository;
import br.edu.atitus.analyticsapi.repositories.InventorySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryIngestionService {

    private final InventorySnapshotRepository snapshotRepository;
    private final InventoryProductStatusRepository productStatusRepository;
    private final InventoryMovementRepository movementRepository;
    private final CheckpointService checkpointService;

    public InventoryIngestionService(
            InventorySnapshotRepository snapshotRepository,
            InventoryProductStatusRepository productStatusRepository,
            InventoryMovementRepository movementRepository,
            CheckpointService checkpointService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.productStatusRepository = productStatusRepository;
        this.movementRepository = movementRepository;
        this.checkpointService = checkpointService;
    }

    /** Grava um novo snapshot e substitui a lista atual de produtos sem estoque/estoque baixo. */
    @Transactional
    public int saveSnapshot(
            SourceInventory.Summary summary,
            List<SourceInventory.Item> outOfStock,
            List<SourceInventory.Item> lowStock,
            Instant capturedAt
    ) {
        snapshotRepository.save(new InventorySnapshotEntity(
                capturedAt,
                summary.inventoryCount(),
                summary.quantityOnHand(),
                summary.quantityReserved(),
                summary.availableQuantity(),
                summary.outOfStockCount(),
                summary.lowStockCount()
        ));

        Map<Long, InventoryProductStatusEntity> statuses = new LinkedHashMap<>();
        for (SourceInventory.Item item : lowStock) {
            if (item != null && item.productId() != null) {
                statuses.put(item.productId(), toStatus(item, false, capturedAt));
            }
        }
        for (SourceInventory.Item item : outOfStock) {
            if (item == null || item.productId() == null) {
                continue;
            }
            InventoryProductStatusEntity existing = statuses.get(item.productId());
            if (existing != null) {
                existing.markOutOfStock();
            } else {
                statuses.put(item.productId(), toStatus(item, true, capturedAt));
            }
        }

        productStatusRepository.deleteAllInBatch();
        productStatusRepository.saveAll(statuses.values());
        checkpointService.advance(CollectionSource.INVENTORY_SNAPSHOT, capturedAt);
        return statuses.size() + 1;
    }

    /** Upsert por id da movimentação: reprocessar a mesma janela não duplica registros. */
    @Transactional
    public int saveMovements(List<SourceInventory.Movement> movements) {
        List<InventoryMovementEntity> entities = movements.stream()
                .filter(movement -> movement != null && movement.id() != null && movement.createdAt() != null)
                .map(movement -> new InventoryMovementEntity(
                        movement.id(),
                        movement.productId(),
                        movement.movementType() == null ? "UNKNOWN" : movement.movementType(),
                        movement.quantityDelta(),
                        movement.reason(),
                        movement.orderId(),
                        movement.reservationId(),
                        movement.createdAt()
                ))
                .toList();
        movementRepository.saveAll(entities);
        return entities.size();
    }

    @Transactional
    public void completeMovementWindow(Instant windowEnd) {
        checkpointService.advance(CollectionSource.INVENTORY_MOVEMENTS, windowEnd);
    }

    private static InventoryProductStatusEntity toStatus(SourceInventory.Item item, boolean outOfStock, Instant now) {
        boolean noStock = outOfStock || item.availableQuantity() <= 0;
        return new InventoryProductStatusEntity(
                item.productId(),
                item.productType(),
                item.quantityOnHand(),
                item.quantityReserved(),
                item.availableQuantity(),
                item.lowStockThreshold(),
                item.lowStock(),
                noStock,
                item.updatedAt(),
                now
        );
    }
}
