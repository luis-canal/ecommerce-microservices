package br.edu.atitus.analyticsapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Progresso persistido de cada fonte de coleta; permite retomar após interrupções. */
@Entity
@Table(name = "collection_checkpoint")
public class CollectionCheckpointEntity {

    @Id
    @Column(length = 64)
    private String source;

    private Instant cursorAt;

    private Instant lastAttemptAt;

    private Instant lastSuccessAt;

    @Column(length = 16)
    private String lastStatus;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    protected CollectionCheckpointEntity() {
    }

    public CollectionCheckpointEntity(String source, Instant cursorAt) {
        this.source = source;
        this.cursorAt = cursorAt;
    }

    public String getSource() {
        return source;
    }

    public Instant getCursorAt() {
        return cursorAt;
    }

    public void setCursorAt(Instant cursorAt) {
        this.cursorAt = cursorAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
