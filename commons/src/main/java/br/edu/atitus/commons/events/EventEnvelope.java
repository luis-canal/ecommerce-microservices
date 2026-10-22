package br.edu.atitus.commons.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(UUID eventId, String type, int version, Instant occurredAt, T payload) {
}