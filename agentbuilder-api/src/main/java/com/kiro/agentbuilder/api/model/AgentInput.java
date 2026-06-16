package com.kiro.agentbuilder.api.model;

import java.util.Map;

public record AgentInput(String content, String sessionId, String userId, Map<String, Object> metadata) {

    public AgentInput {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
