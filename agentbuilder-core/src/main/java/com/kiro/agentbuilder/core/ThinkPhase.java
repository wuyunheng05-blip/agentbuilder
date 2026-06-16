package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelEventType;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.ThinkingDeltaPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseResult;
import com.kiro.agentbuilder.api.tool.ToolDefinition;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class ThinkPhase implements Phase {

    private final PromptComposer promptComposer;
    private final InterceptorChain interceptorChain;
    private final ModelProvider modelProvider;
    private final LlmDecisionParser decisionParser;
    private final List<ToolDefinition> toolDefinitions;
    private final PhaseTraceRecorder traceRecorder;

    public ThinkPhase(
            PromptComposer promptComposer,
            InterceptorChain interceptorChain,
            ModelProvider modelProvider,
            LlmDecisionParser decisionParser,
            List<ToolDefinition> toolDefinitions,
            PhaseTraceRecorder traceRecorder) {
        this.promptComposer = promptComposer;
        this.interceptorChain = interceptorChain;
        this.modelProvider = modelProvider;
        this.decisionParser = decisionParser;
        this.toolDefinitions = List.copyOf(toolDefinitions);
        this.traceRecorder = traceRecorder;
    }

    @Override
    public String name() {
        return "Think";
    }

    @Override
    public Mono<PhaseResult> execute(ExecutionContext context, FluxSink<AgentEvent> sink) {
        int iteration = (int) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0);
        Instant started = Instant.now();
        ModelRequest request = promptComposer.compose(context, toolDefinitions, iteration);
        context.attributes().put(RuntimeAttributes.LAST_MODEL_REQUEST, request);
        return interceptorChain.beforeLlmCall(context, request)
                .flatMapMany(modelProvider::call)
                .doOnNext(event -> forwardThinkingDelta(context, sink, event))
                .flatMap(event -> {
                    if (event.type() == ModelEventType.ERROR && event.error() != null) {
                        return Mono.error(event.error());
                    }
                    return Mono.just(event);
                })
                .filter(event -> event.type() == ModelEventType.COMPLETE)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("ModelProvider did not emit a completion event")))
                .map(ModelEvent::response)
                .flatMap(response -> interceptorChain.afterLlmCall(context, response))
                .doOnNext(response -> {
                    context.tokenBudget().consume(response.tokenUsage().totalTokens());
                    context.attributes().put(RuntimeAttributes.CURRENT_MODEL_RESPONSE, response);
                    context.attributes().put(RuntimeAttributes.CURRENT_DECISION, decisionParser.parse(response));
                    traceRecorder.record(
                            context,
                            iteration,
                            name().toUpperCase(),
                            ((LlmDecision) context.attributes().get(RuntimeAttributes.CURRENT_DECISION)).type().name(),
                            "messages=" + context.messageHistory().size(),
                            response.content(),
                            response.toolCalls().toString(),
                            Duration.between(started, Instant.now()).toMillis(),
                            response.tokenUsage().totalTokens());
                })
                .thenReturn(PhaseResult.continueWith());
    }

    private void forwardThinkingDelta(ExecutionContext context, FluxSink<AgentEvent> sink, ModelEvent event) {
        if (event.type() == ModelEventType.CONTENT_DELTA && event.contentDelta() != null) {
            sink.next(AgentEvent.of(
                    AgentEventType.THINKING_DELTA,
                    context.runId(),
                    new ThinkingDeltaPayload(event.contentDelta())));
        }
    }
}
