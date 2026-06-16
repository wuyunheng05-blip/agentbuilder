package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentStatus;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseResult;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public class TerminationPhase implements Phase {

    private static final String CORRECTIVE_PROMPT = "请明确调用工具或给出最终答案。";

    private final SelfConsistencyValidator validator;

    public TerminationPhase(SelfConsistencyValidator validator) {
        this.validator = validator;
    }

    @Override
    public String name() {
        return "Termination";
    }

    @Override
    public Mono<PhaseResult> execute(ExecutionContext context, FluxSink<AgentEvent> sink) {
        int iteration = (int) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0);
        int attempts = (int) context.attributes().getOrDefault(RuntimeAttributes.ITERATION_ATTEMPTS, iteration + 1);
        LlmDecision decision = (LlmDecision) context.attributes().get(RuntimeAttributes.CURRENT_DECISION);
        context.attributes().put(RuntimeAttributes.NEXT_DIRECTIVE, IterationDirective.NONE);
        if (decision instanceof FinalAnswerDecision finalAnswerDecision) {
            ModelRequest request = (ModelRequest) context.attributes().get(RuntimeAttributes.LAST_MODEL_REQUEST);
            Mono<FinalAnswerDecision> finalDecisionMono = validator.shouldValidate(finalAnswerDecision)
                    ? validator.validate(context, request, sink)
                    : Mono.just(finalAnswerDecision);
            return finalDecisionMono.map(validated -> {
                context.attributes().put(RuntimeAttributes.TERMINAL_PAYLOAD,
                        new FinalPayload(
                                validated.content(),
                                AgentStatus.COMPLETED,
                                validated.tokenUsage(),
                                validated.confidenceScore(),
                                attempts));
                return PhaseResult.terminate("final answer");
            });
        }
        if (decision instanceof ToolCallDecision) {
            context.attributes().put(RuntimeAttributes.NEXT_DIRECTIVE, IterationDirective.NEXT_ITERATION);
            return Mono.just(PhaseResult.continueWith());
        }

        context.addMessage(com.kiro.agentbuilder.api.model.Message.user(CORRECTIVE_PROMPT));
        context.attributes().put(RuntimeAttributes.NEXT_DIRECTIVE, IterationDirective.RETRY_ITERATION);
        return Mono.just(PhaseResult.continueWith());
    }
}
