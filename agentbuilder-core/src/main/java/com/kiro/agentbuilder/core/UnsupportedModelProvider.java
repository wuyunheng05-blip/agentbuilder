package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import reactor.core.publisher.Flux;

public class UnsupportedModelProvider implements ModelProvider {

    @Override
    public String getModelName() {
        return "unsupported";
    }

    @Override
    public Flux<ModelEvent> call(ModelRequest request) {
        return Flux.error(new IllegalStateException("No ModelProvider configured for agent " + request.metadata().get("agentId")));
    }
}
