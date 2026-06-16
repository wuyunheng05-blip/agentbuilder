package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.GuardResult;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Mono;

public class CancellationGuard implements ExecutionGuard {

    @Override
    public Mono<GuardResult> check(ExecutionContext context) {
        if (context.cancellationToken().isCancelled()) {
            return Mono.just(GuardResult.reject("Run cancelled"));
        }
        return Mono.just(GuardResult.allow());
    }
}
