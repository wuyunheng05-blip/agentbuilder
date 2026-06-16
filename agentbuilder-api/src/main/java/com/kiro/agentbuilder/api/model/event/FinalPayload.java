package com.kiro.agentbuilder.api.model.event;

import com.kiro.agentbuilder.api.model.AgentStatus;
import com.kiro.agentbuilder.api.model.TokenUsage;

public record FinalPayload(
        String content,
        AgentStatus status,
        TokenUsage tokenUsage,
        double confidenceScore,
        int iterationCount) {
}
