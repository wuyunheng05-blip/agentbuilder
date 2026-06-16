package com.kiro.agentbuilder.api.storage;

import java.time.Instant;

public record StepTrace(
        String stepId,
        String runId,
        int iteration,
        String phase,
        String decisionType,
        String inputSummary,
        String thinkingSummary,
        String outputSummary,
        long durationMs,
        long tokenCount,
        Instant createdAt) {

    public StepTrace {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
