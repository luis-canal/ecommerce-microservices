package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.OrderApiClient;
import br.edu.atitus.analyticsapi.clients.PageResponse;
import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Simula o order-api (filtro inclusivo updatedAt >= updatedAfter, ordenação por (updatedAt, id)
 * e paginação) e um checkpoint persistente, executando várias coletas seguidas.
 */
class OrderCollectorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final int PAGE_SIZE = 100;

    private final OrderApiClient client = mock(OrderApiClient.class);
    private final OrderIngestionService ingestion = mock(OrderIngestionService.class);
    private final CheckpointService checkpoints = mock(CheckpointService.class);

    /** Estado do order-api: pedido -> versão atual. */
    private final Map<Long, SourceOrder> source = new LinkedHashMap<>();
    /** Estado do analytics: o que foi gravado (upsert por id). */
    private final Map<Long, SourceOrder> stored = new HashMap<>();
    private final List<Integer> requestedPages = new ArrayList<>();
    private CollectionCheckpointEntity checkpoint;
    private long safetyLagSeconds = 0;

    @BeforeEach
    void setUp() {
        checkpoint = new CollectionCheckpointEntity(CollectionSource.ORDERS.name(), null);
        when(checkpoints.getOrCreate(eq(CollectionSource.ORDERS), any())).thenAnswer(inv -> checkpoint);
        doAnswer(inv -> {
            checkpoint.setCursorAt(inv.getArgument(1));
            return null;
        }).when(checkpoints).advance(eq(CollectionSource.ORDERS), any());

        when(client.fetchOrdersUpdatedAfter(any(), anyInt(), anyInt())).thenAnswer(inv -> {
            Instant updatedAfter = inv.getArgument(0);
            int page = inv.getArgument(1);
            int size = inv.getArgument(2);
            requestedPages.add(page);
            List<SourceOrder> matching = source.values().stream()
                    .filter(order -> !order.updatedAt().isBefore(updatedAfter))
                    .sorted(Comparator.comparing(SourceOrder::updatedAt).thenComparing(SourceOrder::id))
                    .toList();
            int from = Math.min(page * size, matching.size());
            int to = Math.min(from + size, matching.size());
            int totalPages = (matching.size() + size - 1) / size;
            return PageResponse.of(new ArrayList<>(matching.subList(from, to)), page, totalPages);
        });

        when(ingestion.ingestPage(anyList())).thenAnswer(inv -> {
            List<SourceOrder> orders = inv.getArgument(0);
            orders.forEach(order -> stored.put(order.id(), order));
            return orders.size();
        });
    }

    private long collect() {
        requestedPages.clear();
        return new OrderCollector(client, ingestion, checkpoints, PAGE_SIZE, T0, safetyLagSeconds).collect();
    }

    private void put(long id, Instant updatedAt, String status) {
        source.put(id, order(id, updatedAt, status));
    }

    @Test
    void newOrderAfterAnEmptyCollectionIsCollected() {
        put(1, T0.plusSeconds(10), "PENDING_PAYMENT");
        put(2, T0.plusSeconds(20), "PENDING_PAYMENT");
        collect();
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(20));

        // Coleta sem novidades: relê só o pedido da fronteira e mantém o cursor.
        collect();
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(20));
        assertThat(requestedPages).containsExactly(0);

        put(3, T0.plusSeconds(30), "PENDING_PAYMENT");
        collect();

        assertThat(stored).containsKeys(1L, 2L, 3L);
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(requestedPages).containsExactly(0);
    }

    @Test
    void statusChangeAfterAnEmptyCollectionIsCaptured() {
        put(1, T0.plusSeconds(10), "PENDING_PAYMENT");
        put(2, T0.plusSeconds(20), "PENDING_PAYMENT");
        collect();
        collect();
        collect();

        put(1, T0.plusSeconds(40), "CANCELLED");
        put(2, T0.plusSeconds(41), "CONFIRMED");
        collect();

        assertThat(stored.get(1L).status()).isEqualTo("CANCELLED");
        assertThat(stored.get(2L).status()).isEqualTo("CONFIRMED");
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(41));
    }

    @Test
    void walksAllPagesInOneRunWhenThereAreMoreThanOnePageOfOrders() {
        for (long id = 1; id <= 250; id++) {
            put(id, T0.plusSeconds(id), "CONFIRMED");
        }

        long processed = collect();

        assertThat(processed).isEqualTo(250);
        assertThat(stored).hasSize(250);
        assertThat(requestedPages).containsExactly(0, 1, 2);
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(250));

        // A execução seguinte volta à página 0 e não perde o próximo pedido.
        put(251L, T0.plusSeconds(251), "CONFIRMED");
        collect();
        assertThat(requestedPages).containsExactly(0);
        assertThat(stored).containsKey(251L);
    }

    @Test
    void walksAllPagesWhenManyOrdersShareTheSameUpdatedAt() {
        for (long id = 1; id <= 150; id++) {
            put(id, T0.plusSeconds(5), "CONFIRMED");
        }

        collect();
        assertThat(stored).hasSize(150);
        assertThat(requestedPages).containsExactly(0, 1);

        put(151L, T0.plusSeconds(6), "CONFIRMED");
        collect();
        assertThat(stored).containsKey(151L);
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(6));
    }

    @Test
    void safetyLagRecoversAnOrderCommittedLateWithAnOlderUpdatedAt() {
        safetyLagSeconds = 5;
        put(1, T0.plusSeconds(10), "PENDING_PAYMENT");
        put(2, T0.plusSeconds(20), "PENDING_PAYMENT");
        collect();
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(15));

        // Pedido cuja transação terminou depois da coleta, com updatedAt anterior ao maior visto.
        put(3, T0.plusSeconds(18), "PENDING_PAYMENT");
        collect();

        assertThat(stored).containsKey(3L);
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(15));
    }

    @Test
    void safetyLagNeverMovesTheCursorBackwards() {
        safetyLagSeconds = 5;
        checkpoint.setCursorAt(T0.plusSeconds(100));
        put(1, T0.plusSeconds(102), "CONFIRMED");

        collect();

        assertThat(stored).containsKey(1L);
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(100));
        verify(checkpoints, never()).advance(any(), any());

        put(2, T0.plusSeconds(130), "CONFIRMED");
        collect();
        assertThat(checkpoint.getCursorAt()).isEqualTo(T0.plusSeconds(125));
    }

    @Test
    void doesNotTouchTheCheckpointWhenThereIsNothingToCollect() {
        long processed = collect();

        assertThat(processed).isZero();
        verify(ingestion, never()).ingestPage(anyList());
        verify(checkpoints, never()).advance(any(), any());
    }

    static SourceOrder order(Long id, Instant updatedAt) {
        return order(id, updatedAt, "CONFIRMED");
    }

    static SourceOrder order(Long id, Instant updatedAt, String status) {
        return new SourceOrder(id, 10L, updatedAt, updatedAt, status, List.of(), null,
                null, null, null, null, "BRL", updatedAt, null);
    }
}
