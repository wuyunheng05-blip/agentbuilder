package com.kiro.agentbuilder.storage;

import com.kiro.agentbuilder.api.storage.ObservabilityStore;
import com.kiro.agentbuilder.api.storage.RunTrace;
import com.kiro.agentbuilder.api.storage.StepTrace;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObservabilityStore implements ObservabilityStore {

    private final Map<String, RunTrace> runs = new ConcurrentHashMap<>();
    private final Map<String, List<StepTrace>> steps = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> saveRunTrace(RunTrace trace) {
        runs.put(trace.runId(), trace);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveStepTrace(StepTrace trace) {
        steps.computeIfAbsent(trace.runId(), ignored -> new ArrayList<>()).add(trace);
        return Mono.empty();
    }

    @Override
    public Mono<Void> updateRunTrace(RunTrace trace) {
        runs.put(trace.runId(), trace);
        return Mono.empty();
    }

    @Override
    public Mono<RunTrace> getRunTrace(String runId) {
        return Mono.justOrEmpty(runs.get(runId));
    }

    @Override
    public Flux<RunTrace> queryRunTraces(String sessionId) {
        return Flux.fromStream(runs.values().stream()
                .filter(trace -> sessionId == null || sessionId.equals(trace.sessionId())));
    }

    @Override
    public Flux<StepTrace> getStepTraces(String runId) {
        return Flux.fromIterable(steps.getOrDefault(runId, List.of()));
    }
}
