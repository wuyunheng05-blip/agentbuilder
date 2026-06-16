package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.ToolCallPayload;
import com.kiro.agentbuilder.api.model.event.ToolResultPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.react.PhaseResult;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ActionPhase implements Phase {

    private final ToolGateway toolGateway;
    private final PhaseTraceRecorder traceRecorder;

    public ActionPhase(ToolGateway toolGateway, PhaseTraceRecorder traceRecorder) {
        this.toolGateway = toolGateway;
        this.traceRecorder = traceRecorder;
    }

    @Override
    public String name() {
        return "Action";
    }

    @Override
    public Mono<PhaseResult> execute(ExecutionContext context, FluxSink<AgentEvent> sink) {
        int iteration = (int) context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0);
        LlmDecision decision = (LlmDecision) context.attributes().get(RuntimeAttributes.CURRENT_DECISION);
        if (!(decision instanceof ToolCallDecision toolCallDecision)) {
            traceRecorder.record(context, iteration, name().toUpperCase(), decision.type().name(),
                    "no tool call", "", "", 0, 0);
            return Mono.just(PhaseResult.continueWith());
        }

        Instant started = Instant.now();
        List<ToolResult> results = new ArrayList<>();
        return Flux.fromIterable(toolCallDecision.toolCalls())
                .concatMap(call -> executeTool(context, sink, call, results))
                .then(Mono.fromSupplier(() -> {
                    context.attributes().put(RuntimeAttributes.CURRENT_TOOL_RESULTS, List.copyOf(results));
                    traceRecorder.record(
                            context,
                            iteration,
                            name().toUpperCase(),
                            DecisionType.TOOL_CALL.name(),
                            toolCallDecision.toolCalls().toString(),
                            "",
                            results.toString(),
                            Duration.between(started, Instant.now()).toMillis(),
                            0);
                    return PhaseResult.continueWith();
                }));
    }

    private Mono<Void> executeTool(
            ExecutionContext context,
            FluxSink<AgentEvent> sink,
            ToolCall call,
            List<ToolResult> results) {
        sink.next(AgentEvent.of(AgentEventType.TOOL_CALL, context.runId(), new ToolCallPayload(call)));
        return toolGateway.execute(call, context)
                .doOnNext(result -> {
                    results.add(result);
                    context.addMessage(Message.tool(call.toolName(), String.valueOf(result.output())));
                    sink.next(AgentEvent.of(AgentEventType.TOOL_RESULT, context.runId(), new ToolResultPayload(result)));
                })
                .then();
    }
}
