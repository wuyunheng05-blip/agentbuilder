package com.kiro.agentbuilder.api.react;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public interface Phase {

    String name();

    Mono<PhaseResult> execute(ExecutionContext context, FluxSink<AgentEvent> sink);
}
