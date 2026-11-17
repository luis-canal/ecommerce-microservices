package br.edu.atitus.analyticsapi.dtos;

import br.edu.atitus.analyticsapi.entities.CollectionRunEntity;

import java.time.Instant;
import java.util.List;

/** Controle de execução das coletas: situação atual de cada fonte e as execuções mais recentes. */
public record CollectionsResponse(
        List<CollectionStatusResponse> sources,
        List<Run> recentRuns
) {

    public record Run(
            Long id,
            String source,
            Instant startedAt,
            Instant finishedAt,
            String status,
            long recordsProcessed,
            String errorMessage
    ) {
        public static Run from(CollectionRunEntity run) {
            return new Run(run.getId(), run.getSource(), run.getStartedAt(), run.getFinishedAt(),
                    run.getStatus(), run.getRecordsProcessed(), run.getErrorMessage());
        }
    }
}
