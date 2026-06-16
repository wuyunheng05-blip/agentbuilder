package com.kiro.agentbuilder.api.model;

import reactor.core.publisher.Flux;

public interface ModelProvider {

    String getModelName();

    Flux<ModelEvent> call(ModelRequest request);
}
