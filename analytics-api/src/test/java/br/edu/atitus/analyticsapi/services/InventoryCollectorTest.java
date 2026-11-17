package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.InventoryApiClient;
import br.edu.atitus.analyticsapi.clients.PageResponse;
import br.edu.atitus.analyticsapi.clients.SourceInventory;
import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Limite de páginas: com páginas pendentes além do limite, a coleta falha sem gravar dados incompletos. */
class InventoryCollectorTest {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 3;

    private final InventoryApiClient client = mock(InventoryApiClient.class);
    private final InventoryIngestionService ingestion = mock(InventoryIngestionService.class);
    private final CheckpointService checkpoints = mock(CheckpointService.class);
    private final InventoryCollector collector =
            new InventoryCollector(client, ingestion, checkpoints, PAGE_SIZE, 90, 5, MAX_PAGES);

    @BeforeEach
    void setUp() {
        CollectionCheckpointEntity checkpoint = new CollectionCheckpointEntity(
                CollectionSource.INVENTORY_MOVEMENTS.name(), Instant.now().minusSeconds(3600));
        when(checkpoints.getOrCreate(eq(CollectionSource.INVENTORY_MOVEMENTS), any())).thenReturn(checkpoint);
        when(ingestion.saveMovements(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        when(client.fetchSummary()).thenReturn(new SourceInventory.Summary(1, 1, 0, 1, 0, 0));
        when(client.fetchLowStock(anyInt(), anyInt())).thenReturn(PageResponse.of(List.of(), 0, 0));
    }

    @Test
    void movementsFailWithoutCompletingTheWindowWhenPagesRemainBeyondTheLimit() {
        when(client.fetchMovements(any(), any(), anyInt(), anyInt()))
                .thenAnswer(inv -> PageResponse.of(List.of(movement(inv.getArgument(2))), inv.getArgument(2), 10));

        assertThatThrownBy(collector::collectMovements)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Limite de 3 páginas");

        verify(client, never()).fetchMovements(any(), any(), eq(MAX_PAGES), anyInt());
        verify(ingestion, never()).completeMovementWindow(any());
    }

    @Test
    void movementsCompleteTheWindowWhenTheLastPageFitsTheLimit() {
        when(client.fetchMovements(any(), any(), anyInt(), anyInt()))
                .thenAnswer(inv -> PageResponse.of(List.of(movement(inv.getArgument(2))), inv.getArgument(2), MAX_PAGES));

        long processed = collector.collectMovements();

        assertThat(processed).isEqualTo(MAX_PAGES);
        verify(ingestion).completeMovementWindow(any());
    }

    @Test
    void snapshotFailsBeforeSavingWhenPagesRemainBeyondTheLimit() {
        when(client.fetchOutOfStock(anyInt(), anyInt()))
                .thenAnswer(inv -> PageResponse.of(List.of(item(inv.getArgument(0))), inv.getArgument(0), 10));

        assertThatThrownBy(collector::collectSnapshot)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("produtos sem estoque");

        verify(ingestion, never()).saveSnapshot(any(), anyList(), anyList(), any());
    }

    @Test
    void snapshotIsSavedWhenAllPagesFitTheLimit() {
        when(client.fetchOutOfStock(anyInt(), anyInt()))
                .thenAnswer(inv -> PageResponse.of(List.of(item(inv.getArgument(0))), inv.getArgument(0), MAX_PAGES));

        collector.collectSnapshot();

        verify(ingestion).saveSnapshot(any(), anyList(), anyList(), any());
    }

    private static SourceInventory.Movement movement(int page) {
        return new SourceInventory.Movement((long) page + 1, 10L, "ENTRY", 1, "teste", null, null, Instant.now());
    }

    private static SourceInventory.Item item(int page) {
        return new SourceInventory.Item((long) page + 1, "MULTI_UNIT", 0, 0, 0, 1, true, Instant.now());
    }
}
