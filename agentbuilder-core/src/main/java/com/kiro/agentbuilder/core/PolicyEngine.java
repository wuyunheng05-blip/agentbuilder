package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class PolicyEngine {

    private final HitlHandler hitlHandler;

    public PolicyEngine(HitlHandler hitlHandler) {
        this.hitlHandler = hitlHandler;
    }

    public Mono<PolicyResult> validate(Tool tool, ToolCall call, ExecutionContext context) {
        long estimatedTokens = readLong(call.arguments().get("estimatedTokens"), 50L);
        if (context.tokenBudget().remaining() < estimatedTokens) {
            return Mono.just(PolicyResult.reject("Tool call exceeds remaining token budget"));
        }

        Duration timeout = Duration.ofMillis(readLong(call.arguments().get("timeoutMs"), defaultTimeoutMillis(tool.getRiskLevel())));
        if (timeout.isNegative() || timeout.isZero()) {
            return Mono.just(PolicyResult.reject("Invalid timeout for tool call"));
        }

        if (tool.getRiskLevel() == RiskLevel.HIGH || tool.getRiskLevel() == RiskLevel.CRITICAL) {
            return hitlHandler.requestApproval(tool, call, context)
                    .map(decision -> decision.approved()
                            ? PolicyResult.allow(timeout)
                            : PolicyResult.reject("HITL rejected tool call: " + decision.reason()));
        }

        return Mono.just(PolicyResult.allow(timeout));
    }

    private long defaultTimeoutMillis(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 10_000L;
            case MEDIUM -> 20_000L;
            case HIGH -> 30_000L;
            case CRITICAL -> 60_000L;
        };
    }

    private long readLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
