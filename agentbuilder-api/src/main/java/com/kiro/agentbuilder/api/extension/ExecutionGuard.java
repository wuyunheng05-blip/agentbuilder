package com.kiro.agentbuilder.api.extension;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Mono;

public interface ExecutionGuard {

    Mono<GuardResult> check(ExecutionContext context);
}
