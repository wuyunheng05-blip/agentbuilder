package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.TokenUsage;

public record FinalAnswerDecision(String content, double confidenceScore, TokenUsage tokenUsage) implements LlmDecision {

    @Override
    public DecisionType type() {
        return DecisionType.FINAL_ANSWER;
    }
}
