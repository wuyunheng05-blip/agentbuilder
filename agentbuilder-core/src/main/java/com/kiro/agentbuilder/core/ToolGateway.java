package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolGateway {

    private final ToolRegistry toolRegistry;
    private final InterceptorChain interceptorChain;
    private final com.kiro.agentbuilder.api.spi.AuthorizationService authorizationService;
    private final PolicyEngine policyEngine;
    private final JsonSchemaValidator schemaValidator;
    private final Map<String, ToolResult> idempotentCache = new ConcurrentHashMap<>();

    public ToolGateway(
            ToolRegistry toolRegistry,
            InterceptorChain interceptorChain,
            com.kiro.agentbuilder.api.spi.AuthorizationService authorizationService,
            PolicyEngine policyEngine,
            JsonSchemaValidator schemaValidator) {
        this.toolRegistry = toolRegistry;
        this.interceptorChain = interceptorChain;
        this.authorizationService = authorizationService;
        this.policyEngine = policyEngine;
        this.schemaValidator = schemaValidator;
    }

    public Mono<ToolResult> execute(ToolCall call, ExecutionContext context) {
        return interceptorChain.beforeToolCall(context, call)
                .switchIfEmpty(Mono.error(new IllegalStateException("Tool call was blocked by interceptor")))
                .flatMap(preparedCall -> toolRegistry.find(preparedCall.toolName())
                        .map(tool -> authorizeAndExecute(tool, preparedCall, context))
                        .orElseGet(() -> Mono.just(ToolResult.error(preparedCall.callId(), "Tool not found: " + preparedCall.toolName()))));
    }

    private Mono<ToolResult> authorizeAndExecute(Tool tool, ToolCall call, ExecutionContext context) {
        Principal principal = context.attributes().get("principal") instanceof Principal p ? p : null;
        return authorizationService.authorize(principal, tool.getName(), "execute")
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.just(ToolResult.error(call.callId(), "Unauthorized tool call: " + tool.getName()));
                    }
                    String cacheKey = tool.getName() + ":" + call.arguments().hashCode();
                    if (tool.isIdempotent() && idempotentCache.containsKey(cacheKey)) {
                        return Mono.just(idempotentCache.get(cacheKey));
                    }
                    return policyEngine.validate(tool, call, context)
                            .flatMap(policyResult -> {
                                if (!policyResult.allowed()) {
                                    return Mono.just(ToolResult.error(call.callId(), policyResult.reason()));
                                }
                                schemaValidator.validate(tool.getParameterSchema(), call.arguments());
                                return tool.execute(call, context)
                                        .timeout(policyResult.timeout())
                                        .flatMap(result -> interceptorChain.afterToolCall(context, result));
                            })
                            .doOnNext(result -> {
                                if (tool.isIdempotent() && !result.error()) {
                                    idempotentCache.put(cacheKey, result);
                                }
                            });
                });
    }
}
