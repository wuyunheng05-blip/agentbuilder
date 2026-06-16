package com.kiro.agentbuilder.test;

import com.kiro.agentbuilder.api.Agent;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import com.kiro.agentbuilder.core.AgentBuilders;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeSmokeTest {

    @Test
    void shouldExecuteToolAndProduceFinalAnswer() {
        AtomicInteger round = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "test-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                if (round.getAndIncrement() == 0) {
                    return Flux.just(
                            ModelEvent.delta("need tool"),
                            ModelEvent.complete(new ModelResponse(
                                    "",
                                    List.of(new ToolCall("echo", Map.of("value", "hello"), "call-1")),
                                    new TokenUsage(10, 5, 15),
                                    0.6,
                                    Map.of())));
                }
                return Flux.just(
                        ModelEvent.delta("done"),
                        ModelEvent.complete(new ModelResponse(
                                "final: hello",
                                List.of(),
                                new TokenUsage(6, 8, 14),
                                0.9,
                                Map.of())));
            }
        };

        Agent agent = AgentBuilders.builder()
                .id("smoke-agent")
                .systemPrompt("You are helpful.")
                .model(provider)
                .tool(echoTool())
                .build();

        List<AgentEvent> events = agent.run(new AgentInput("say hello", "session-1", "user-1", Map.of()))
                .collectList()
                .block();

        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.RUN_START));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.THINKING_DELTA));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.TOOL_CALL));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.TOOL_RESULT));
        AgentEvent finalEvent = events.stream()
                .filter(event -> event.type() == AgentEventType.FINAL)
                .findFirst()
                .orElseThrow();
        FinalPayload payload = (FinalPayload) finalEvent.payload();
        assertEquals("final: hello", payload.content());
        assertEquals(2, payload.iterationCount());
    }

    @Test
    void shouldRetryWhenModelProducesNoAction() {
        AtomicInteger round = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "test-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                if (round.getAndIncrement() == 0) {
                    return Flux.just(ModelEvent.complete(new ModelResponse("", List.of(), TokenUsage.empty(), 0.1d, Map.of())));
                }
                return Flux.just(ModelEvent.complete(new ModelResponse("after retry", List.of(), TokenUsage.empty(), 0.9d, Map.of())));
            }
        };

        Agent agent = AgentBuilders.builder()
                .id("retry-agent")
                .systemPrompt("You are helpful.")
                .model(provider)
                .build();

        List<AgentEvent> events = agent.run(new AgentInput("answer please", "session-2", "user-2", Map.of()))
                .collectList()
                .block();

        AgentEvent finalEvent = events.stream()
                .filter(event -> event.type() == AgentEventType.FINAL)
                .findFirst()
                .orElseThrow();
        FinalPayload payload = (FinalPayload) finalEvent.payload();
        assertEquals("after retry", payload.content());
        assertEquals(2, payload.iterationCount());
        assertEquals(2, round.get());
    }

    private Tool echoTool() {
        return new Tool() {
            @Override
            public String getName() {
                return "echo";
            }

            @Override
            public String getDescription() {
                return "Echo tool";
            }

            @Override
            public JsonSchema getParameterSchema() {
                return JsonSchema.empty();
            }

            @Override
            public RiskLevel getRiskLevel() {
                return RiskLevel.LOW;
            }

            @Override
            public boolean isIdempotent() {
                return true;
            }

            @Override
            public Mono<ToolResult> execute(ToolCall call, com.kiro.agentbuilder.api.model.ExecutionContext context) {
                return Mono.just(ToolResult.success(call.callId(), call.arguments().get("value")));
            }
        };
    }
}
