package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentStatus;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseResult;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public class PreCheckPhase implements Phase {

    private final int maxIterations;
    private final long minReservedTokens;

    public PreCheckPhase(int maxIterations, long minReservedTokens) {
        this.maxIterations = maxIterations;
        this.minReservedTokens = minReservedTokens;
    }

    @Override
    public String name() {
        return "PreCheck";
    }

    @Override
    public Mono<PhaseResult> execute(ExecutionContext context, FluxSink<AgentEvent> sink) {
        int iteration = (int) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0);
        int attempts = (int) context.attributes().getOrDefault(RuntimeAttributes.ITERATION_ATTEMPTS, 0);
        if (context.cancellationToken().isCancelled()) {
            context.attributes().put(RuntimeAttributes.TERMINAL_PAYLOAD,
                    new FinalPayload("", AgentStatus.CANCELLED, TokenUsage.empty(), 0.0d, attempts));
            return Mono.just(PhaseResult.terminate("cancelled"));
        }
        if (context.isTimedOut()) {
            context.attributes().put(RuntimeAttributes.TERMINAL_PAYLOAD,
                    new FinalPayload("", AgentStatus.TIMEOUT, TokenUsage.empty(), 0.0d, attempts));
            return Mono.just(PhaseResult.terminate("timeout"));
        }
        if (iteration >= maxIterations) {
            context.attributes().put(RuntimeAttributes.TERMINAL_PAYLOAD,
                    new FinalPayload("Reached max iterations", AgentStatus.ERROR, TokenUsage.empty(), 0.0d, attempts));
            return Mono.just(PhaseResult.terminate("max iterations"));
        }
        if (context.tokenBudget().remaining() <= minReservedTokens) {
            context.attributes().put(RuntimeAttributes.TERMINAL_PAYLOAD,
                    new FinalPayload("Token budget exhausted", AgentStatus.ERROR, TokenUsage.empty(), 0.0d, attempts));
            return Mono.just(PhaseResult.terminate("token budget exhausted"));
        }
        return Mono.just(PhaseResult.continueWith());
    }
}
