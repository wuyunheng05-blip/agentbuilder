package com.kiro.agentbuilder.core;

public record NoActionDecision(String reason) implements LlmDecision {

    @Override
    public DecisionType type() {
        return DecisionType.NO_ACTION;
    }
}
