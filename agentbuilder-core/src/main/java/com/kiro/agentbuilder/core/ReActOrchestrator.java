package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseAction;
import com.kiro.agentbuilder.api.react.PhaseResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.List;

public class ReActOrchestrator {

    private final List<Phase> phases;

    public ReActOrchestrator(List<Phase> phases) {
        this.phases = List.copyOf(phases);
    }

    public Mono<FinalPayload> orchestrate(ExecutionContext context, FluxSink<AgentEvent> sink) {
        int initialIteration = ((Number) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0)).intValue();
        return runIteration(context, sink, initialIteration)
                .then(Mono.defer(() -> Mono.justOrEmpty((FinalPayload) context.attributes().get(RuntimeAttributes.TERMINAL_PAYLOAD))))
                .switchIfEmpty(Mono.error(new IllegalStateException("Orchestration completed without terminal payload")));
    }

    private Mono<Void> runIteration(ExecutionContext context, FluxSink<AgentEvent> sink, int iteration) {
        context.attributes().put(RuntimeAttributes.CURRENT_ITERATION, iteration);
        int attempts = ((Number) context.attributes().getOrDefault(RuntimeAttributes.ITERATION_ATTEMPTS, 0)).intValue() + 1;
        context.attributes().put(RuntimeAttributes.ITERATION_ATTEMPTS, attempts);
        return runPhase(context, sink, iteration, 0);
    }

    private Mono<Void> runPhase(ExecutionContext context, FluxSink<AgentEvent> sink, int iteration, int phaseIndex) {
        if (phaseIndex >= phases.size()) {
            return runIteration(context, sink, iteration + 1);
        }
        Phase phase = phases.get(phaseIndex);
        return phase.execute(context, sink)
                .flatMap(result -> handlePhaseResult(context, sink, iteration, phaseIndex, result));
    }

    private Mono<Void> handlePhaseResult(
            ExecutionContext context,
            FluxSink<AgentEvent> sink,
            int iteration,
            int phaseIndex,
            PhaseResult result) {
        PhaseAction action = result.action();
        return switch (action) {
            case CONTINUE, ASYNC_BOUNDARY -> runPhase(context, sink, iteration, phaseIndex + 1);
            case TERMINATE -> Mono.empty();
            case RETRY_ITERATION -> runIteration(context, sink, iteration);
            case NEXT_ITERATION -> runIteration(context, sink, iteration + 1);
        };
    }
}
