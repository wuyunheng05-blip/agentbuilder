package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.storage.StepTrace;

import java.time.Instant;
import java.util.UUID;

public class PhaseTraceRecorder {

    private final LifecycleHookChain hookChain;

    public PhaseTraceRecorder(LifecycleHookChain hookChain) {
        this.hookChain = hookChain;
    }

    public void record(
            ExecutionContext context,
            int iteration,
            String phase,
            String decisionType,
            String inputSummary,
            String thinkingSummary,
            String outputSummary,
            long durationMs,
            long tokenCount) {
        hookChain.onStep(context, new StepTrace(
                UUID.randomUUID().toString(),
                context.runId(),
                iteration,
                phase,
                decisionType,
                inputSummary,
                thinkingSummary,
                outputSummary,
                durationMs,
                tokenCount,
                Instant.now()));
    }
}
