package com.kiro.agentbuilder.api;

import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import reactor.core.publisher.Flux;

public interface Agent {

    String getId();

    Flux<AgentEvent> run(AgentInput input);

    Flux<AgentEvent> resume(String snapshotId, AgentInput input);

    void shutdown();
}
