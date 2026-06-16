package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.AgentInterceptor;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class InterceptorChain {

    private final List<AgentInterceptor> interceptors;

    public InterceptorChain(List<AgentInterceptor> interceptors) {
        this.interceptors = List.copyOf(interceptors);
    }

    public Mono<AgentInput> beforeRun(ExecutionContext context, AgentInput input) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.beforeRun(context, input)))
                .last(input);
    }

    public Mono<AgentEvent> afterRun(ExecutionContext context, AgentEvent finalEvent) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.afterRun(context, finalEvent)))
                .last(finalEvent);
    }

    public Mono<ModelRequest> beforeLlmCall(ExecutionContext context, ModelRequest request) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.beforeLlmCall(context, request)))
                .last(request);
    }

    public Mono<ModelResponse> afterLlmCall(ExecutionContext context, ModelResponse response) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.afterLlmCall(context, response)))
                .last(response);
    }

    public Mono<ToolCall> beforeToolCall(ExecutionContext context, ToolCall call) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.beforeToolCall(context, call)))
                .last(call);
    }

    public Mono<ToolResult> afterToolCall(ExecutionContext context, ToolResult result) {
        return Flux.fromIterable(interceptors)
                .concatMap(interceptor -> Mono.defer(() -> interceptor.afterToolCall(context, result)))
                .last(result);
    }
}
