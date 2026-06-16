package com.kiro.agentbuilder.api.storage;

import com.kiro.agentbuilder.api.model.AgentStatus;
import com.kiro.agentbuilder.api.model.TokenUsage;

import java.time.Instant;

public record RunTrace(
        String runId,
        String sessionId,
        String agentId,
        AgentStatus status,
        long durationMs,
        TokenUsage tokenUsage,
        String strategy,
        String modelName,
        int stepCount,
        Instant createdAt) {

    public RunTrace {
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
