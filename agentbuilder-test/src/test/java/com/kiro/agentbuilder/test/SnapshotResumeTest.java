package com.kiro.agentbuilder.test;

import com.kiro.agentbuilder.api.Agent;
import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import com.kiro.agentbuilder.core.AgentBuilders;
import com.kiro.agentbuilder.core.AgentRuntime;
import com.kiro.agentbuilder.core.CircuitBreakerOpenException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotResumeTest {

    @Test
    void shouldPersistLatestSnapshotDuringRun() {
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "snapshot-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                return Flux.just(ModelEvent.complete(new ModelResponse(
                        "snapshot-final",
                        List.of(),
                        new TokenUsage(1, 2, 3),
                        0.95,
                        Map.of())));
            }
        };

        AgentConfig config = AgentBuilders.builder()
                .id("snapshot-agent")
                .model(provider)
                .sessionId("snapshot-session")
                .preview();

        Agent agent = new AgentRuntime(config);
        agent.run(new AgentInput("hello", "snapshot-session", "user-1", Map.of())).collectList().block();

        AgentSnapshot snapshot = config.storageModule().snapshotStore().findLatest("snapshot-session").block();
        assertNotNull(snapshot);
        assertEquals("snapshot-session", snapshot.sessionId());
        assertTrue(snapshot.snapshotData().containsKey("messages"));
    }

    @Test
    void shouldResumeFromLatestSnapshotAfterInterruptedRun() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "resume-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                int invocation = calls.getAndIncrement();
                if (invocation == 0) {
                    return Flux.just(ModelEvent.complete(new ModelResponse(
                            "",
                            List.of(new ToolCall("echo_resume", Map.of("value", "cached"), "tool-1")),
                            new TokenUsage(2, 1, 3),
                            0.3,
                            Map.of())));
                }
                if (invocation == 1) {
                    return Flux.error(new CircuitBreakerOpenException("simulated interruption"));
                }
                return Flux.just(ModelEvent.complete(new ModelResponse(
                        "resumed-final",
                        List.of(),
                        new TokenUsage(1, 2, 3),
                        0.9,
                        Map.of())));
            }
        };

        Tool echoTool = new Tool() {
            @Override
            public String getName() {
                return "echo_resume";
            }

            @Override
            public String getDescription() {
                return "Echo tool for resume";
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

        AgentConfig config = AgentBuilders.builder()
                .id("resume-agent")
                .model(provider)
                .tool(echoTool)
                .sessionId("resume-session")
                .preview();

        Agent agent = new AgentRuntime(config);
        List<AgentEvent> failedRunEvents = agent.run(new AgentInput("start", "resume-session", "user-1", Map.of()))
                .collectList()
                .block();

        assertTrue(failedRunEvents.stream().anyMatch(event -> event.type() == AgentEventType.ERROR));

        AgentSnapshot snapshot = config.storageModule().snapshotStore().findLatest("resume-session").block();
        assertNotNull(snapshot);

        List<AgentEvent> resumedEvents = agent.resume(snapshot.snapshotId(), new AgentInput("", "resume-session", "user-1", Map.of()))
                .collectList()
                .block();

        AgentEvent finalEvent = resumedEvents.stream()
                .filter(event -> event.type() == AgentEventType.FINAL)
                .findFirst()
                .orElseThrow();
        FinalPayload payload = (FinalPayload) finalEvent.payload();
        assertEquals("resumed-final", payload.content());
        assertFalse(resumedEvents.stream().anyMatch(event -> event.type() == AgentEventType.ERROR));
    }
}
