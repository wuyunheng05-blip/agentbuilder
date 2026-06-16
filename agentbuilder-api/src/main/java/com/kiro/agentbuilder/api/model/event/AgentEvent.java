package com.kiro.agentbuilder.api.model.event;

import java.time.Instant;

public record AgentEvent(AgentEventType type, String runId, Instant timestamp, Object payload) {

    public AgentEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static AgentEvent of(AgentEventType type, String runId, Object payload) {
        return new AgentEvent(type, runId, Instant.now(), payload);
    }
}
