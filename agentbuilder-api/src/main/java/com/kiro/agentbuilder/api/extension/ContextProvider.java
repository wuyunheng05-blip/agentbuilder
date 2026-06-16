package com.kiro.agentbuilder.api.extension;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Mono;

public interface ContextProvider {

    default Mono<Void> inject(ExecutionContext context) {
        return Mono.empty();
    }

    default Mono<Void> extract(ExecutionContext context) {
        return Mono.empty();
    }
}
