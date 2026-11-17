package br.edu.atitus.analyticsapi.services;

import br.edu.atitus.analyticsapi.entities.CollectionCheckpointEntity;
import br.edu.atitus.analyticsapi.entities.CollectionRunEntity;
import br.edu.atitus.analyticsapi.enums.CollectionSource;
import br.edu.atitus.analyticsapi.enums.CollectionStatus;
import br.edu.atitus.analyticsapi.repositories.CollectionCheckpointRepository;
import br.edu.atitus.analyticsapi.repositories.CollectionRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CheckpointService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final CollectionCheckpointRepository checkpointRepository;
    private final CollectionRunRepository runRepository;

    public CheckpointService(
            CollectionCheckpointRepository checkpointRepository,
            CollectionRunRepository runRepository
    ) {
        this.checkpointRepository = checkpointRepository;
        this.runRepository = runRepository;
    }

    @Transactional
    public CollectionCheckpointEntity getOrCreate(CollectionSource source, Instant initialCursor) {
        return checkpointRepository.findById(source.name())
                .orElseGet(() -> checkpointRepository.save(new CollectionCheckpointEntity(source.name(), initialCursor)));
    }

    /** Atualiza o cursor da fonte; participa da transação corrente, se houver. */
    @Transactional
    public void advance(CollectionSource source, Instant cursorAt) {
        CollectionCheckpointEntity checkpoint = checkpointRepository.findById(source.name())
                .orElseGet(() -> new CollectionCheckpointEntity(source.name(), cursorAt));
        checkpoint.setCursorAt(cursorAt);
        checkpointRepository.save(checkpoint);
    }

    @Transactional
    public Long start(CollectionSource source) {
        Instant now = Instant.now();
        CollectionCheckpointEntity checkpoint = checkpointRepository.findById(source.name())
                .orElseGet(() -> new CollectionCheckpointEntity(source.name(), null));
        checkpoint.setLastAttemptAt(now);
        checkpoint.setLastStatus(CollectionStatus.RUNNING.name());
        checkpointRepository.save(checkpoint);
        return runRepository.save(new CollectionRunEntity(source.name(), now, CollectionStatus.RUNNING.name())).getId();
    }

    @Transactional
    public void finish(CollectionSource source, Long runId, long records, Exception failure) {
        Instant now = Instant.now();
        CollectionStatus status = failure == null ? CollectionStatus.SUCCESS : CollectionStatus.FAILED;
        String error = failure == null ? null : truncate(failure.getMessage());

        checkpointRepository.findById(source.name()).ifPresent(checkpoint -> {
            checkpoint.setLastStatus(status.name());
            checkpoint.setLastError(error);
            if (failure == null) {
                checkpoint.setLastSuccessAt(now);
            }
        });
        runRepository.findById(runId).ifPresent(run -> {
            run.setFinishedAt(now);
            run.setStatus(status.name());
            run.setRecordsProcessed(records);
            run.setErrorMessage(error);
        });
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Erro sem mensagem.";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
