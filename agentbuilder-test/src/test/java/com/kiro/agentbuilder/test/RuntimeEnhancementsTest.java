package com.kiro.agentbuilder.test;

import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.CancellationToken;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenBudget;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import com.kiro.agentbuilder.core.AgentBuilders;
import com.kiro.agentbuilder.core.AgentRuntime;
import com.kiro.agentbuilder.core.ApprovalDecision;
import com.kiro.agentbuilder.core.HitlHandler;
import com.kiro.agentbuilder.core.InterceptorChain;
import com.kiro.agentbuilder.core.JsonSchemaValidator;
import com.kiro.agentbuilder.core.PolicyEngine;
import com.kiro.agentbuilder.core.ToolGateway;
import com.kiro.agentbuilder.core.ToolRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEnhancementsTest {

    @Test
    void shouldRejectHighRiskToolWhenHitlDenies() {
        AtomicBoolean executed = new AtomicBoolean(false);
        Tool highRiskTool = new Tool() {
            @Override
            public String getName() {
                return "dangerous_write";
            }

            @Override
            public String getDescription() {
                return "High risk operation";
            }

            @Override
            public JsonSchema getParameterSchema() {
                return new JsonSchema(Map.of(
                        "type", "object",
                        "required", List.of("target"),
                        "properties", Map.of("target", Map.of("type", "string"))));
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
                executed.set(true);
                return Mono.just(ToolResult.success(call.callId(), "done"));
            }
        };

        ToolGateway gateway = new ToolGateway(
                new ToolRegistry(List.of(highRiskTool)),
                new InterceptorChain(List.of()),
                (principal, resource, action) -> Mono.just(true),
                new PolicyEngine(new HitlHandler() {
                    @Override
                    public Mono<ApprovalDecision> requestApproval(Tool tool, ToolCall call, ExecutionContext context) {
                        return Mono.just(ApprovalDecision.rejected("manual rejection"));
                    }
                }),
                new JsonSchemaValidator());

        ToolResult result = gateway.execute(
                new ToolCall("dangerous_write", Map.of("target", "secrets.txt"), "risk-1"),
                new ExecutionContext(
                        "run-1",
                        "agent-1",
                        "session-1",
                        List.of(),
                        new TokenBudget(1000),
                        0,
                        new CancellationToken(),
                        Map.of(),
                        Instant.now(),
                        Duration.ofMinutes(1))).block();

        assertTrue(result.error());
        assertFalse(executed.get());
    }

    @Test
    void shouldPersistRunAndStepTrace() {
        ModelProvider provider = new ModelProvider() {
            @Override
            public String getModelName() {
                return "trace-model";
            }

            @Override
            public Flux<ModelEvent> call(ModelRequest request) {
                return Flux.just(ModelEvent.complete(new ModelResponse(
                        "trace-final",
                        List.of(),
                        new TokenUsage(2, 3, 5),
                        0.95,
                        Map.of())));
            }
        };

        AgentConfig config = AgentBuilders.builder()
                .id("trace-agent")
                .systemPrompt("trace")
                .model(provider)
                .sessionId("trace-session")
                .preview();

        AgentRuntime runtime = new AgentRuntime(config);
        runtime.run(new AgentInput("hello", "trace-session", "trace-user", Map.of()))
                .collectList()
                .block();

        var runTraces = config.storageModule().observabilityStore().queryRunTraces("trace-session").collectList().block();
        assertEquals(1, runTraces.size());
        assertEquals("trace-agent", runTraces.get(0).agentId());
        assertEquals(2, runTraces.get(0).stepCount());
        assertEquals(5, runTraces.get(0).tokenUsage().totalTokens());

        var stepTraces = config.storageModule().observabilityStore().getStepTraces(runTraces.get(0).runId()).collectList().block();
        assertEquals(2, stepTraces.size());
        assertTrue(stepTraces.stream().anyMatch(trace -> "THINK".equals(trace.phase())));
    }
}
