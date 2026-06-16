package com.kiro.agentbuilder.api.model;

public record TokenUsage(long inputTokens, long outputTokens, long totalTokens) {

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                totalTokens + other.totalTokens);
    }
}
