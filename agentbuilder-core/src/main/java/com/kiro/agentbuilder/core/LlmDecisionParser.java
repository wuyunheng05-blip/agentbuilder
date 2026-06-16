package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ModelResponse;

public class LlmDecisionParser {

    public LlmDecision parse(ModelResponse response) {
        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            return new ToolCallDecision(response.toolCalls());
        }
        if (response.content() != null && !response.content().isBlank()) {
            return new FinalAnswerDecision(response.content(), response.confidenceScore(), response.tokenUsage());
        }
        return new NoActionDecision("Model response did not contain tool calls or final content");
    }
}
