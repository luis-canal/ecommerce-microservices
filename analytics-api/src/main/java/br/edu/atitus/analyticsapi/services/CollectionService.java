package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.dtos.CollectionStatusResponse;
import br.edu.atitus.analyticsapi.dtos.CollectionsResponse;
import br.edu.atitus.analyticsapi.repositories.CollectionRunRepository;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import br.edu.atitus.analyticsapi.repositories.CollectionCheckpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Orquestra a coleta periódica. Cada fonte é executada de forma independente: a falha de
 * uma não impede as demais, e todas registram execução e progresso no analytics_db.
 * A coleta roda fora de qualquer fluxo transacional dos serviços de origem.
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final OrderCollector orderCollector;
    private final InventoryCollector inventoryCollector;
    private final CheckpointService checkpointService;
    private final CollectionCheckpointRepository checkpointRepository;
    private final CollectionRunRepository runRepository;
    private final boolean enabled;
    private final ReentrantLock lock = new ReentrantLock();

    public CollectionService(
            OrderCollector orderCollector,
            InventoryCollector inventoryCollector,
            CheckpointService checkpointService,
            CollectionCheckpointRepository checkpointRepository,
            CollectionRunRepository runRepository,
            @Value("${analytics.collection.enabled:true}") boolean enabled
    ) {
        this.orderCollector = orderCollector;
        this.inventoryCollector = inventoryCollector;
        this.checkpointService = checkpointService;
        this.checkpointRepository = checkpointRepository;
        this.runRepository = runRepository;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${analytics.collection.interval-ms:300000}",
            initialDelayString = "${analytics.collection.initial-delay-ms:30000}"
    )
    public void scheduledCollection() {
        if (enabled) {
            runAll();
        }
    }

    /** @return false se já existe uma coleta em andamento. */
    public boolean runAll() {
        if (!lock.tryLock()) {
            log.info("Coleta analítica já em andamento; execução ignorada.");
            return false;
        }
        try {
            run(CollectionSource.ORDERS, orderCollector::collect);
            run(CollectionSource.INVENTORY_SNAPSHOT, inventoryCollector::collectSnapshot);
            run(CollectionSource.INVENTORY_MOVEMENTS, inventoryCollector::collectMovements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Situação de cada fonte e as 20 execuções mais recentes. */
    public CollectionsResponse status() {
        List<CollectionStatusResponse> sources = Arrays.stream(CollectionSource.values())
                .map(source -> checkpointRepository.findById(source.name())
                        .map(CollectionStatusResponse::from)
                        .orElseGet(() -> CollectionStatusResponse.never(source.name())))
                .toList();
        List<CollectionsResponse.Run> recentRuns = runRepository.findTop20ByOrderByStartedAtDescIdDesc().stream()
                .map(CollectionsResponse.Run::from)
                .toList();
        return new CollectionsResponse(sources, recentRuns);
    }

    private void run(CollectionSource source, LongSupplier collector) {
        Long runId = checkpointService.start(source);
        try {
            long records = collector.getAsLong();
            checkpointService.finish(source, runId, records, null);
            log.info("Coleta {} concluída: {} registros.", source, records);
        } catch (RuntimeException exception) {
            log.warn("Coleta {} falhou: {}", source, exception.getMessage());
            checkpointService.finish(source, runId, 0, exception);
        }
    }
}
