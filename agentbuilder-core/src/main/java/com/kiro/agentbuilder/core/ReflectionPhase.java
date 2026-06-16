package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseResult;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public class ReflectionPhase implements Phase {

    private final PhaseTraceRecorder traceRecorder;

    public ReflectionPhase(PhaseTraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    @Override
    public String name() {
        return "Reflection";
    }

    @Override
    public Mono<PhaseResult> execute(ExecutionContext context, FluxSink<com.kiro.agentbuilder.api.model.event.AgentEvent> sink) {
        int iteration = (int) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0);
        LlmDecision decision = (LlmDecision) context.attributes().get(RuntimeAttributes.CURRENT_DECISION);
        ModelResponse response = (ModelResponse) context.attributes().get(RuntimeAttributes.CURRENT_MODEL_RESPONSE);
        IterationDirective directive = (IterationDirective) context.attributes().getOrDefault(RuntimeAttributes.NEXT_DIRECTIVE, IterationDirective.NONE);
        traceRecorder.record(
                context,
                iteration,
                name().toUpperCase(),
                decision.type().name(),
                "directive=" + directive.name(),
                response == null ? "" : response.content(),
                "",
                0,
                0);
        return switch (directive) {
            case NEXT_ITERATION -> Mono.just(PhaseResult.nextIteration("tool execution finished"));
            case RETRY_ITERATION -> Mono.just(PhaseResult.retryIteration("retry with corrective prompt"));
            case NONE -> Mono.just(PhaseResult.continueWith());
        };
    }
}
