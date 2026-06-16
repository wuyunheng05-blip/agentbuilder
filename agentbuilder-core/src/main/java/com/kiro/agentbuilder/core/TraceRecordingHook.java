package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.AgentStatus;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.ObservabilityStore;
import com.kiro.agentbuilder.api.storage.RunTrace;
import com.kiro.agentbuilder.api.storage.StepTrace;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TraceRecordingHook implements LifecycleHook {

    private final ObservabilityStore observabilityStore;
    private final String agentId;
    private final String modelName;
    private final String strategy;
    private final Map<String, AtomicInteger> stepCounts = new ConcurrentHashMap<>();

    public TraceRecordingHook(ObservabilityStore observabilityStore, String agentId, String modelName, String strategy) {
        this.observabilityStore = observabilityStore;
        this.agentId = agentId;
        this.modelName = modelName;
        this.strategy = strategy;
    }

    @Override
    public void onStart(ExecutionContext context, AgentInput input) {
        stepCounts.put(context.runId(), new AtomicInteger());
        observabilityStore.saveRunTrace(new RunTrace(
                context.runId(),
                context.sessionId(),
                agentId,
                AgentStatus.RUNNING,
                0L,
                TokenUsage.empty(),
                strategy,
                modelName,
                0,
                Instant.now())).block();
    }

    @Override
    public void onStep(ExecutionContext context, StepTrace step) {
        stepCounts.computeIfAbsent(context.runId(), ignored -> new AtomicInteger()).incrementAndGet();
        observabilityStore.saveStepTrace(step).block();
    }

    @Override
    public void onComplete(ExecutionContext context, FinalPayload payload) {
        observabilityStore.updateRunTrace(new RunTrace(
                context.runId(),
                context.sessionId(),
                agentId,
                payload.status(),
                Duration.between(context.startTime(), Instant.now()).toMillis(),
                totalTokenUsage(context, payload),
                strategy,
                modelName,
                stepCounts.getOrDefault(context.runId(), new AtomicInteger()).get(),
                context.startTime())).block();
        stepCounts.remove(context.runId());
    }

    @Override
    public void onError(ExecutionContext context, Throwable error) {
        observabilityStore.updateRunTrace(new RunTrace(
                context.runId(),
                context.sessionId(),
                agentId,
                AgentStatus.ERROR,
                Duration.between(context.startTime(), Instant.now()).toMillis(),
                new TokenUsage(0, 0, context.tokenBudget().consumed()),
                strategy,
                modelName,
                stepCounts.getOrDefault(context.runId(), new AtomicInteger()).get(),
                context.startTime())).block();
        stepCounts.remove(context.runId());
    }

    private TokenUsage totalTokenUsage(ExecutionContext context, FinalPayload payload) {
        TokenUsage payloadUsage = payload.tokenUsage() == null ? TokenUsage.empty() : payload.tokenUsage();
        long total = Math.max(payloadUsage.totalTokens(), context.tokenBudget().consumed());
        return new TokenUsage(payloadUsage.inputTokens(), payloadUsage.outputTokens(), total);
    }
}
