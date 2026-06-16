package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.GuardResult;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Mono;

public class RecursionDepthGuard implements ExecutionGuard {

    private final int maxDepth;

    public RecursionDepthGuard(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public Mono<GuardResult> check(ExecutionContext context) {
        if (context.recursionDepth() > maxDepth) {
            return Mono.just(GuardResult.reject("Recursion depth exceeded: " + context.recursionDepth()));
        }
        return Mono.just(GuardResult.allow());
    }
}
