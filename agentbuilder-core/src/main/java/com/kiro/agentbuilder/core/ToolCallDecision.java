package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.tool.ToolCall;

import java.util.List;

public record ToolCallDecision(List<ToolCall> toolCalls) implements LlmDecision {

    public ToolCallDecision {
        toolCalls = List.copyOf(toolCalls);
    }

    @Override
    public DecisionType type() {
        return DecisionType.TOOL_CALL;
    }
}
