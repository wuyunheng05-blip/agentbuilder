package com.kiro.agentbuilder.api.memory;

import java.time.Instant;
import java.util.Map;

public record MemoryEntry(
        String id,
        String agentInstanceId,
        MemoryType type,
        String content,
        float[] embedding,
        double importanceScore,
        Instant createdAt,
        Map<String, Object> metadata) {

    public MemoryEntry {
        embedding = embedding == null ? null : embedding.clone();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
