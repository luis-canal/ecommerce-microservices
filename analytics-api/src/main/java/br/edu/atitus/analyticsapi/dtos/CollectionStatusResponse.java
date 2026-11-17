package br.edu.atitus.analyticsapi.dtos;

import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;

import java.time.Instant;

public record CollectionStatusResponse(
        String source,
        Instant cursorAt,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastStatus,
        String lastError
) {
    public static CollectionStatusResponse from(CollectionCheckpointEntity checkpoint) {
        return new CollectionStatusResponse(
                checkpoint.getSource(),
                checkpoint.getCursorAt(),
                checkpoint.getLastAttemptAt(),
                checkpoint.getLastSuccessAt(),
                checkpoint.getLastStatus(),
                checkpoint.getLastError()
        );
    }

    public static CollectionStatusResponse never(String source) {
        return new CollectionStatusResponse(source, null, null, null, "NEVER_RUN", null);
    }
}
