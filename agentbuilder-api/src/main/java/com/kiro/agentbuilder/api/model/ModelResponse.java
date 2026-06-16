package com.kiro.agentbuilder.api.model;

import com.kiro.agentbuilder.api.tool.ToolCall;

import java.util.List;
import java.util.Map;

public record ModelResponse(
        String content,
        List<ToolCall> toolCalls,
        TokenUsage tokenUsage,
        double confidenceScore,
        Map<String, Object> metadata) {

    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
