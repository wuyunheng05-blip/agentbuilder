package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelEventType;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.AggregateResultPayload;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class SelfConsistencyValidator {

    private final ModelProvider modelProvider;
    private final InterceptorChain interceptorChain;
    private final int samplingCount;
    private final double confidenceThreshold;

    public SelfConsistencyValidator(
            ModelProvider modelProvider,
            InterceptorChain interceptorChain,
            int samplingCount,
            double confidenceThreshold) {
        this.modelProvider = modelProvider;
        this.interceptorChain = interceptorChain;
        this.samplingCount = samplingCount;
        this.confidenceThreshold = confidenceThreshold;
    }

    public boolean shouldValidate(FinalAnswerDecision decision) {
        return decision.confidenceScore() < confidenceThreshold;
    }

    public Mono<FinalAnswerDecision> validate(ExecutionContext context, ModelRequest request, FluxSink<AgentEvent> sink) {
        return Flux.range(0, samplingCount)
                .flatMap(index -> sample(context, request))
                .collectList()
                .map(responses -> selectFinalDecision(responses, sink, context));
    }

    private Mono<ModelResponse> sample(ExecutionContext context, ModelRequest request) {
        return interceptorChain.beforeLlmCall(context, request)
                .flatMapMany(modelProvider::call)
                .filter(event -> event.type() != ModelEventType.ERROR)
                .filter(event -> event.type() == ModelEventType.COMPLETE)
                .map(ModelEvent::response)
                .next()
                .flatMap(response -> interceptorChain.afterLlmCall(context, response));
    }

    private FinalAnswerDecision selectFinalDecision(
            List<ModelResponse> responses,
            FluxSink<AgentEvent> sink,
            ExecutionContext context) {
        Map<String, Long> grouped = responses.stream()
                .map(ModelResponse::content)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(java.util.stream.Collectors.groupingBy(Function.identity(), LinkedHashMap::new, java.util.stream.Collectors.counting()));

        String selectedKey = grouped.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("");

        ModelResponse selectedResponse = responses.stream()
                .filter(response -> normalize(response.content()).equals(selectedKey))
                .findFirst()
                .orElse(responses.get(0));

        double confidence = responses.isEmpty() ? 0.0d : (double) grouped.getOrDefault(selectedKey, 0L) / responses.size();
        sink.next(AgentEvent.of(
                AgentEventType.AGGREGATE_RESULT,
                context.runId(),
                new AggregateResultPayload(
                        responses.stream().map(ModelResponse::content).toList(),
                        selectedResponse.content(),
                        confidence)));
        TokenUsage usage = responses.stream()
                .map(ModelResponse::tokenUsage)
                .reduce(TokenUsage.empty(), TokenUsage::plus);
        return new FinalAnswerDecision(selectedResponse.content(), confidence, usage);
    }

    private String normalize(String content) {
        return content == null ? "" : content.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
