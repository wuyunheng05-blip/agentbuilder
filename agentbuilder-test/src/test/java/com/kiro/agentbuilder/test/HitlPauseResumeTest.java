package com.kiro.agentbuilder.test;

import com.kiro.agentbuilder.api.Agent;
import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.ApprovalRequiredPayload;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import com.kiro.agentbuilder.core.AgentBuilders;
import com.kiro.agentbuilder.core.AgentRuntime;
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

class HitlPauseResumeTest {

    @Test
    void shouldPauseHighRiskToolAndResumeAfterApproval() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolExecutions = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "hitl-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                int invocation = modelCalls.getAndIncrement();
                if (invocation == 0) {
                    return Flux.just(ModelEvent.complete(new ModelResponse(
                            "",
                            List.of(new ToolCall("dangerous_write", Map.of("target", "approved.txt"), "tool-risk-1")),
                            new TokenUsage(2, 1, 3),
                            0.4,
                            Map.of())));
                }
                return Flux.just(ModelEvent.complete(new ModelResponse(
                        "approved-final",
                        List.of(),
                        new TokenUsage(1, 2, 3),
                        0.92,
                        Map.of())));
            }
        };

        Tool highRiskTool = new Tool() {
            @Override
            public String getName() {
                return "dangerous_write";
            }

            @Override
            public String getDescription() {
                return "High risk write";
            }

            @Override
            public JsonSchema getParameterSchema() {
                return JsonSchema.empty();
            }

            @Override
            public RiskLevel getRiskLevel() {
                return RiskLevel.HIGH;
            }

            @Override
            public boolean isIdempotent() {
                return false;
            }

            @Override
            public Mono<ToolResult> execute(ToolCall call, ExecutionContext context) {
                toolExecutions.incrementAndGet();
                return Mono.just(ToolResult.success(call.callId(), "written"));
            }
        };

        AgentConfig config = AgentBuilders.builder()
                .id("hitl-agent")
                .model(provider)
                .tool(highRiskTool)
                .sessionId("hitl-session")
                .preview();

        Agent agent = new AgentRuntime(config);
        List<AgentEvent> pausedEvents = agent.run(new AgentInput("start", "hitl-session", "user-1", Map.of()))
                .collectList()
                .block();

        AgentEvent approvalEvent = pausedEvents.stream()
                .filter(event -> event.type() == AgentEventType.APPROVAL_REQUIRED)
                .findFirst()
                .orElseThrow();
        ApprovalRequiredPayload payload = (ApprovalRequiredPayload) approvalEvent.payload();
        assertEquals(0, toolExecutions.get());
        assertNotNull(payload.approvalRequestId());
        assertNotNull(payload.snapshotId());
        assertFalse(pausedEvents.stream().anyMatch(event -> event.type() == AgentEventType.FINAL));

        AgentSnapshot snapshot = config.storageModule().snapshotStore().load(payload.snapshotId()).block();
        assertNotNull(snapshot);
        assertEquals("APPROVAL_REQUIRED", snapshot.triggerType());

        List<AgentEvent> resumedEvents = agent.resume(
                        payload.snapshotId(),
                        new AgentInput(
                                "",
                                "hitl-session",
                                "user-1",
                                Map.of(
                                        "approvalRequestId", payload.approvalRequestId(),
                                        "approved", true)))
                .collectList()
                .block();

        AgentEvent finalEvent = resumedEvents.stream()
                .filter(event -> event.type() == AgentEventType.FINAL)
                .findFirst()
                .orElseThrow();
        FinalPayload finalPayload = (FinalPayload) finalEvent.payload();
        assertEquals("approved-final", finalPayload.content());
        assertEquals(1, toolExecutions.get());
        assertTrue(resumedEvents.stream().noneMatch(event -> event.type() == AgentEventType.APPROVAL_REQUIRED));
    }
}
