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

    /**
     * Limite de segurança contra laços infinitos. Com páginas de 100 itens cabem 1 milhão de
     * registros por execução, o que comporta com folga a primeira janela de 90 dias.
     */
    static final int DEFAULT_MAX_PAGES = 10_000;

    private final InventoryApiClient inventoryApiClient;
    private final InventoryIngestionService ingestionService;
    private final CheckpointService checkpointService;
    private final int pageSize;
    private final Duration initialLookback;
    private final Duration safetyLag;
    private final int maxPages;

    public InventoryCollector(
            InventoryApiClient inventoryApiClient,
            InventoryIngestionService ingestionService,
            CheckpointService checkpointService,
            @Value("${analytics.collection.page-size:100}") int pageSize,
            @Value("${analytics.collection.initial-movement-lookback-days:90}") long lookbackDays,
            @Value("${analytics.collection.movement-safety-lag-seconds:5}") long safetyLagSeconds,
            @Value("${analytics.collection.max-pages:" + DEFAULT_MAX_PAGES + "}") int maxPages
    ) {
        this.inventoryApiClient = inventoryApiClient;
        this.ingestionService = ingestionService;
        this.checkpointService = checkpointService;
        this.pageSize = Math.max(1, Math.min(pageSize, 200));
        this.initialLookback = Duration.ofDays(Math.max(0, lookbackDays));
        this.safetyLag = Duration.ofSeconds(Math.max(0, safetyLagSeconds));
        this.maxPages = Math.max(1, maxPages);
    }

    /** Snapshot periódico: resumo + listas de produtos sem estoque e com estoque baixo. */
    public long collectSnapshot() {
        SourceInventory.Summary summary = inventoryApiClient.fetchSummary();
        List<SourceInventory.Item> outOfStock = fetchAll(page -> inventoryApiClient.fetchOutOfStock(page, pageSize), "produtos sem estoque");
        List<SourceInventory.Item> lowStock = fetchAll(page -> inventoryApiClient.fetchLowStock(page, pageSize), "produtos com estoque baixo");
        return ingestionService.saveSnapshot(summary, outOfStock, lowStock, Instant.now());
    }

    /**
     * Movimentações em janela fixa [cursor, agora - margem]. A janela só é marcada como
     * concluída depois de todas as páginas gravadas; em caso de falha, ou se o limite de páginas
     * for atingido com mais páginas pendentes, a mesma janela é reprocessada (upsert por id,
     * sem duplicação).
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
        while (true) {
            PageResponse<SourceInventory.Movement> response = inventoryApiClient.fetchMovements(from, to, page, pageSize);
            List<SourceInventory.Movement> movements = response.safeContent();
            if (!movements.isEmpty()) {
                processed += ingestionService.saveMovements(movements);
            }
            if (movements.isEmpty() || !response.hasNext(page, pageSize)) {
                break;
            }
            page++;
            requireWithinPageLimit(page, "movimentações de estoque");
        }
        ingestionService.completeMovementWindow(to);
        return processed;
    }

    private <T> List<T> fetchAll(IntFunction<PageResponse<T>> fetcher, String description) {
        List<T> all = new ArrayList<>();
        int page = 0;
        while (true) {
            PageResponse<T> response = fetcher.apply(page);
            all.addAll(response.safeContent());
            if (response.safeContent().isEmpty() || !response.hasNext(page, pageSize)) {
                return all;
            }
            page++;
            requireWithinPageLimit(page, description);
        }
    }

    /** Falha a coleta em vez de gravar dados incompletos quando ainda há páginas além do limite. */
    private void requireWithinPageLimit(int nextPage, String description) {
        if (nextPage >= maxPages) {
            throw new IllegalStateException("Limite de " + maxPages + " páginas atingido ao coletar "
                    + description + " com páginas pendentes; a coleta será repetida.");
        }
    }
}
