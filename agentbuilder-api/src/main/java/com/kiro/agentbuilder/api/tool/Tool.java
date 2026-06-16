package com.kiro.agentbuilder.api.tool;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import reactor.core.publisher.Mono;

public interface Tool {

    String getName();

    String getDescription();

    JsonSchema getParameterSchema();

    RiskLevel getRiskLevel();

    boolean isIdempotent();

    Mono<ToolResult> execute(ToolCall call, ExecutionContext context);
}
