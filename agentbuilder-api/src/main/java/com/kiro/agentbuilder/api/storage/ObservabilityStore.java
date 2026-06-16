package com.kiro.agentbuilder.api.storage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ObservabilityStore {

    Mono<Void> saveRunTrace(RunTrace trace);

    Mono<Void> saveStepTrace(StepTrace trace);

    Mono<Void> updateRunTrace(RunTrace trace);

    Mono<RunTrace> getRunTrace(String runId);

    Flux<StepTrace> getStepTraces(String runId);
}
