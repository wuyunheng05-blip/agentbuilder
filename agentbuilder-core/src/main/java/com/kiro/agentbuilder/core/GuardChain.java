package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.GuardResult;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class GuardChain {

    private final List<ExecutionGuard> guards;

    public GuardChain(List<ExecutionGuard> guards) {
        this.guards = List.copyOf(guards);
    }

    public Mono<GuardResult> check(ExecutionContext context) {
        return Flux.fromIterable(guards)
                .concatMap(guard -> guard.check(context))
                .filter(result -> !result.allowed())
                .next()
                .switchIfEmpty(Mono.just(GuardResult.allow()));
    }
}
