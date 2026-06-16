package com.kiro.agentbuilder.api.extension;

import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import reactor.core.publisher.Mono;

public interface AgentInterceptor {

    default Mono<AgentInput> beforeRun(ExecutionContext context, AgentInput input) {
        return Mono.just(input);
    }

    default Mono<AgentEvent> afterRun(ExecutionContext context, AgentEvent finalEvent) {
        return Mono.just(finalEvent);
    }

    default Mono<ModelRequest> beforeLlmCall(ExecutionContext context, ModelRequest request) {
        return Mono.just(request);
    }

    default Mono<ModelResponse> afterLlmCall(ExecutionContext context, ModelResponse response) {
        return Mono.just(response);
    }

    default Mono<ToolCall> beforeToolCall(ExecutionContext context, ToolCall call) {
        return Mono.just(call);
    }

    default Mono<ToolResult> afterToolCall(ExecutionContext context, ToolResult result) {
        return Mono.just(result);
    }
}
