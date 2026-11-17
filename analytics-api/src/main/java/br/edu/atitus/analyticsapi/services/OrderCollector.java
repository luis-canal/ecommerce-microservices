package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.OrderApiClient;
import br.edu.atitus.analyticsapi.clients.PageResponse;
import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Coleta incremental de pedidos via updatedAfter.
 *
 * <p>O order-api filtra com {@code updatedAt >= updatedAfter} (inclusivo) e ordena por
 * (updatedAt, id). Cada execução usa o cursor salvo como filtro fixo, começa na página 0,
 * percorre todas as páginas até o fim e só então grava como novo cursor o maior updatedAt
 * visto, recuado por uma margem de segurança. O número da página nunca é persistido.</p>
 *
 * <p>A margem (padrão de 5 s, como nas movimentações de estoque) cobre pedidos gravados com
 * updatedAt um pouco anterior ao maior visto, mas cuja transação ainda não estava concluída no
 * momento da leitura, e pedidos que mudaram de posição na paginação durante a coleta. Os
 * pedidos dentro da margem são relidos na execução seguinte, o que é inofensivo porque a
 * gravação é um upsert por orderId. O cursor nunca retrocede. Se a execução falhar no meio,
 * o cursor não avança e a próxima execução relê as páginas.</p>
 */
@Service
public class OrderCollector {

    private final OrderApiClient orderApiClient;
    private final OrderIngestionService ingestionService;
    private final CheckpointService checkpointService;
    private final int pageSize;
    private final Instant initialCursor;
    private final Duration safetyLag;

    public OrderCollector(
            OrderApiClient orderApiClient,
            OrderIngestionService ingestionService,
            CheckpointService checkpointService,
            @Value("${analytics.collection.page-size:100}") int pageSize,
            @Value("${analytics.collection.initial-order-cursor:1970-01-01T00:00:00Z}") Instant initialCursor,
            @Value("${analytics.collection.order-safety-lag-seconds:5}") long safetyLagSeconds
    ) {
        this.orderApiClient = orderApiClient;
        this.ingestionService = ingestionService;
        this.checkpointService = checkpointService;
        this.pageSize = Math.max(1, Math.min(pageSize, 500));
        this.initialCursor = initialCursor;
        this.safetyLag = Duration.ofSeconds(Math.max(0, safetyLagSeconds));
    }

    public long collect() {
        CollectionCheckpointEntity checkpoint = checkpointService.getOrCreate(CollectionSource.ORDERS, initialCursor);
        Instant cursor = checkpoint.getCursorAt() != null ? checkpoint.getCursorAt() : initialCursor;

        Instant maxUpdatedAt = cursor;
        long processed = 0;
        int page = 0;
        while (true) {
            PageResponse<SourceOrder> response = orderApiClient.fetchOrdersUpdatedAfter(cursor, page, pageSize);
            List<SourceOrder> orders = response.safeContent();
            if (orders.isEmpty()) {
                break;
            }
            processed += ingestionService.ingestPage(orders);
            for (SourceOrder order : orders) {
                if (order != null && order.updatedAt() != null && order.updatedAt().isAfter(maxUpdatedAt)) {
                    maxUpdatedAt = order.updatedAt();
                }
            }
            if (!response.hasNext(page, pageSize)) {
                break;
            }
            page++;
        }

        Instant nextCursor = maxUpdatedAt.minus(safetyLag);
        if (nextCursor.isAfter(cursor)) {
            checkpointService.advance(CollectionSource.ORDERS, nextCursor);
        }
        return processed;
    }
}
