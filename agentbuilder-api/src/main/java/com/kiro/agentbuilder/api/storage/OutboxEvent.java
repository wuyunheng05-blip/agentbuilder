package com.kiro.agentbuilder.api.storage;

import java.time.Instant;
import java.util.Map;

public record OutboxEvent(
        String eventId,
        String eventType,
        Map<String, Object> payload,
        int retryCount,
        Instant createdAt,
        Instant publishedAt) {

    public OutboxEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
