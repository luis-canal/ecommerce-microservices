package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.clients.SourceOrder;
import br.edu.atitus.analyticsapi.entities.AnalyticsOrderEntity;
import br.edu.atitus.analyticsapi.repositories.AnalyticsOrderRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persiste uma página de pedidos de forma idempotente (upsert por orderId) e recalcula as
 * agregações dos dias afetados na mesma transação. O checkpoint não é tocado aqui: o
 * OrderCollector só o avança depois de percorrer todas as páginas; se o processo cair no
 * meio, a execução seguinte relê tudo a partir do cursor anterior sem duplicar dados.
 */
@Service
public class OrderIngestionService {

    private final AnalyticsOrderRepository orderRepository;
    private final OrderFactMapper mapper;
    private final SalesAggregationService aggregationService;
    private final EntityManager entityManager;

    public OrderIngestionService(
            AnalyticsOrderRepository orderRepository,
            OrderFactMapper mapper,
            SalesAggregationService aggregationService,
            EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.aggregationService = aggregationService;
        this.entityManager = entityManager;
    }

    @Transactional
    public int ingestPage(List<SourceOrder> orders) {
        Instant now = Instant.now();
        Set<LocalDate> affectedDates = new HashSet<>();
        int processed = 0;

        for (SourceOrder source : orders) {
            if (source == null || source.id() == null) {
                continue;
            }
            AnalyticsOrderEntity entity = orderRepository.findById(source.id()).orElse(null);
            boolean isNew = entity == null;
            if (!isNew) {
                if (source.updatedAt() != null && entity.getSourceUpdatedAt().isAfter(source.updatedAt())) {
                    // Versão mais antiga do que a já armazenada: ignora.
                    continue;
                }
                if (entity.isCountsAsSale()) {
                    affectedDates.add(entity.getSaleDate());
                }
            } else {
                entity = new AnalyticsOrderEntity();
            }

            mapper.apply(source, entity, now);
            if (entity.isCountsAsSale()) {
                affectedDates.add(entity.getSaleDate());
            }
            if (isNew) {
                entityManager.persist(entity);
            }
            processed++;
        }

        orderRepository.flush();
        aggregationService.recalculate(affectedDates);
        return processed;
    }
}
