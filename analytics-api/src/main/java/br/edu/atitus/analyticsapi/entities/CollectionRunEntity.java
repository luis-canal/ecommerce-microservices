package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Histórico de execuções da coleta. */
@Entity
@Table(name = "collection_run")
public class CollectionRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private long recordsProcessed;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected CollectionRunEntity() {
    }

    public CollectionRunEntity(String source, Instant startedAt, String status) {
        this.source = source;
        this.startedAt = startedAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(long recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
