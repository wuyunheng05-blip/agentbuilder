package com.kiro.agentbuilder.api.storage;

import java.time.Instant;
import java.util.Map;

public record AgentSnapshot(
        String snapshotId,
        String sessionId,
        String triggerType,
        Map<String, Object> snapshotData,
        Instant expiresAt,
        Instant createdAt) {

    public AgentSnapshot {
        snapshotData = snapshotData == null ? Map.of() : Map.copyOf(snapshotData);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
