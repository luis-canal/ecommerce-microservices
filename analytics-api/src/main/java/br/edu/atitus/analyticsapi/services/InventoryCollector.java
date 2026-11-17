package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.InventoryApiClient;
import br.edu.atitus.analyticsapi.clients.PageResponse;
import br.edu.atitus.analyticsapi.clients.SourceInventory;
import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

@Service
public class InventoryCollector {

    private static final int MAX_PAGES = 10_000;

    private final InventoryApiClient inventoryApiClient;
    private final InventoryIngestionService ingestionService;
    private final CheckpointService checkpointService;
    private final int pageSize;
    private final Duration initialLookback;
    private final Duration safetyLag;

    public InventoryCollector(
            InventoryApiClient inventoryApiClient,
            InventoryIngestionService ingestionService,
            CheckpointService checkpointService,
            @Value("${analytics.collection.page-size:100}") int pageSize,
            @Value("${analytics.collection.initial-movement-lookback-days:90}") long lookbackDays,
            @Value("${analytics.collection.movement-safety-lag-seconds:5}") long safetyLagSeconds
    ) {
        this.inventoryApiClient = inventoryApiClient;
        this.ingestionService = ingestionService;
        this.checkpointService = checkpointService;
        this.pageSize = Math.max(1, Math.min(pageSize, 200));
        this.initialLookback = Duration.ofDays(Math.max(0, lookbackDays));
        this.safetyLag = Duration.ofSeconds(Math.max(0, safetyLagSeconds));
    }

    /** Snapshot periódico: resumo + listas de produtos sem estoque e com estoque baixo. */
    public long collectSnapshot() {
        SourceInventory.Summary summary = inventoryApiClient.fetchSummary();
        List<SourceInventory.Item> outOfStock = fetchAll(page -> inventoryApiClient.fetchOutOfStock(page, pageSize));
        List<SourceInventory.Item> lowStock = fetchAll(page -> inventoryApiClient.fetchLowStock(page, pageSize));
        return ingestionService.saveSnapshot(summary, outOfStock, lowStock, Instant.now());
    }

    /**
     * Movimentações em janela fixa [cursor, agora - margem]. A janela só é marcada como
     * concluída depois de todas as páginas gravadas; em caso de falha a mesma janela é
     * reprocessada (upsert por id, sem duplicação).
     */
    public long collectMovements() {
        Instant now = Instant.now();
        CollectionCheckpointEntity checkpoint = checkpointService.getOrCreate(
                CollectionSource.INVENTORY_MOVEMENTS, now.minus(initialLookback));
        Instant from = checkpoint.getCursorAt() != null ? checkpoint.getCursorAt() : now.minus(initialLookback);
        Instant to = now.minus(safetyLag);
        if (!to.isAfter(from)) {
            return 0;
        }

        long processed = 0;
        int page = 0;
        while (page < MAX_PAGES) {
            PageResponse<SourceInventory.Movement> response = inventoryApiClient.fetchMovements(from, to, page, pageSize);
            List<SourceInventory.Movement> movements = response.safeContent();
            if (!movements.isEmpty()) {
                processed += ingestionService.saveMovements(movements);
            }
            if (movements.isEmpty() || !response.hasNext(page, pageSize)) {
                break;
            }
            page++;
        }
        ingestionService.completeMovementWindow(to);
        return processed;
    }

    private <T> List<T> fetchAll(IntFunction<PageResponse<T>> fetcher) {
        List<T> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            PageResponse<T> response = fetcher.apply(page);
            all.addAll(response.safeContent());
            if (response.safeContent().isEmpty() || !response.hasNext(page, pageSize)) {
                break;
            }
        }
        return all;
    }
}
