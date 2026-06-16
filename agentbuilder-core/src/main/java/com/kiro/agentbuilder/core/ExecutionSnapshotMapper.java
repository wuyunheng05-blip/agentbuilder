package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.MessageRole;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.tool.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExecutionSnapshotMapper {

    public AgentSnapshot toSnapshot(ExecutionContext context, String triggerType) {
        Map<String, Object> snapshotData = new HashMap<>();
        snapshotData.put("runId", context.runId());
        snapshotData.put("messages", serializeMessages(context.messageHistory()));
        snapshotData.put("tokenBudget", Map.of(
                "totalBudget", context.tokenBudget().totalBudget(),
                "consumed", context.tokenBudget().consumed()));
        snapshotData.put("recursionDepth", context.recursionDepth());
        snapshotData.put("currentIteration", context.attributes().getOrDefault(RuntimeAttributes.CURRENT_ITERATION, 0));
        snapshotData.put("currentPhaseIndex", context.attributes().getOrDefault(RuntimeAttributes.CURRENT_PHASE_INDEX, 0));
        snapshotData.put("iterationAttempts", context.attributes().getOrDefault(RuntimeAttributes.ITERATION_ATTEMPTS, 0));
        Object pendingApprovalRequestId = context.attributes().get(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID);
        if (pendingApprovalRequestId != null) {
            snapshotData.put("pendingApprovalRequestId", pendingApprovalRequestId);
        }
        Map<String, Object> serializedDecision = serializeDecision((LlmDecision) context.attributes().get(RuntimeAttributes.CURRENT_DECISION));
        if (!serializedDecision.isEmpty()) {
            snapshotData.put("currentDecision", serializedDecision);
        }
        return new AgentSnapshot(
                UUID.randomUUID().toString(),
                context.sessionId(),
                triggerType,
                snapshotData,
                null,
                Instant.now());
    }

    @SuppressWarnings("unchecked")
    public List<Message> restoreMessages(AgentSnapshot snapshot) {
        Object rawMessages = snapshot.snapshotData().get("messages");
        if (!(rawMessages instanceof List<?> list)) {
            return List.of();
        }
        List<Message> restored = new ArrayList<>();
        for (Object rawMessage : list) {
            if (!(rawMessage instanceof Map<?, ?> map)) {
                continue;
            }
            restored.add(new Message(
                    MessageRole.valueOf(String.valueOf(map.get("role"))),
                    map.containsKey("content") ? String.valueOf(map.get("content")) : "",
                    map.get("name") == null ? null : String.valueOf(map.get("name")),
                    map.get("timestamp") == null ? null : Instant.parse(String.valueOf(map.get("timestamp"))),
                    map.get("metadata") instanceof Map<?, ?> metadata ? (Map<String, Object>) metadata : Map.of()));
        }
        return restored;
    }

    @SuppressWarnings("unchecked")
    public long restoreTotalBudget(AgentSnapshot snapshot, long defaultTotalBudget) {
        Object rawBudget = snapshot.snapshotData().get("tokenBudget");
        if (rawBudget instanceof Map<?, ?> budgetMap) {
            Object totalBudget = budgetMap.get("totalBudget");
            if (totalBudget instanceof Number number) {
                return number.longValue();
            }
        }
        return defaultTotalBudget;
    }

    public long restoreConsumedBudget(AgentSnapshot snapshot) {
        Object rawBudget = snapshot.snapshotData().get("tokenBudget");
        if (rawBudget instanceof Map<?, ?> budgetMap) {
            Object consumed = budgetMap.get("consumed");
            if (consumed instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    public int restoreRecursionDepth(AgentSnapshot snapshot) {
        return intValue(snapshot.snapshotData().get("recursionDepth"), 0);
    }

    public int restoreCurrentIteration(AgentSnapshot snapshot) {
        return intValue(snapshot.snapshotData().get("currentIteration"), 0);
    }

    public int restoreIterationAttempts(AgentSnapshot snapshot) {
        return intValue(snapshot.snapshotData().get("iterationAttempts"), 0);
    }

    public int restoreCurrentPhaseIndex(AgentSnapshot snapshot) {
        return intValue(snapshot.snapshotData().get("currentPhaseIndex"), 0);
    }

    public String restorePendingApprovalRequestId(AgentSnapshot snapshot) {
        Object value = snapshot.snapshotData().get("pendingApprovalRequestId");
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public LlmDecision restoreDecision(AgentSnapshot snapshot) {
        Object rawDecision = snapshot.snapshotData().get("currentDecision");
        if (!(rawDecision instanceof Map<?, ?> map)) {
            return null;
        }
        String type = String.valueOf(map.get("type"));
        if (DecisionType.TOOL_CALL.name().equals(type)) {
            Object rawCalls = map.get("toolCalls");
            if (!(rawCalls instanceof List<?> calls)) {
                return null;
            }
            List<ToolCall> toolCalls = new ArrayList<>();
            for (Object rawCall : calls) {
                if (!(rawCall instanceof Map<?, ?> callMap)) {
                    continue;
                }
                Map<String, Object> arguments = callMap.get("arguments") instanceof Map<?, ?> argumentMap
                        ? (Map<String, Object>) argumentMap
                        : Map.of();
                toolCalls.add(new ToolCall(
                        String.valueOf(callMap.get("toolName")),
                        arguments,
                        callMap.get("callId") == null ? null : String.valueOf(callMap.get("callId"))));
            }
            return new ToolCallDecision(toolCalls);
        }
        if (DecisionType.FINAL_ANSWER.name().equals(type)) {
            Map<String, Object> tokenUsageMap = map.get("tokenUsage") instanceof Map<?, ?> rawTokenUsage
                    ? (Map<String, Object>) rawTokenUsage
                    : Map.of();
            return new FinalAnswerDecision(
                    map.get("content") == null ? "" : String.valueOf(map.get("content")),
                    doubleValue(map.get("confidenceScore"), 0.0d),
                    new TokenUsage(
                            longValue(tokenUsageMap.get("inputTokens"), 0L),
                            longValue(tokenUsageMap.get("outputTokens"), 0L),
                            longValue(tokenUsageMap.get("totalTokens"), 0L)));
        }
        if (DecisionType.NO_ACTION.name().equals(type)) {
            return new NoActionDecision(map.get("reason") == null ? "" : String.valueOf(map.get("reason")));
        }
        return null;
    }

    public String restoreRunId(AgentSnapshot snapshot) {
        Object runId = snapshot.snapshotData().get("runId");
        return runId == null ? null : String.valueOf(runId);
    }

    private Map<String, Object> serializeDecision(LlmDecision decision) {
        if (decision == null) {
            return Map.of();
        }
        if (decision instanceof ToolCallDecision toolCallDecision) {
            return Map.of(
                    "type", DecisionType.TOOL_CALL.name(),
                    "toolCalls", toolCallDecision.toolCalls().stream()
                            .map(call -> Map.<String, Object>of(
                                    "toolName", call.toolName(),
                                    "arguments", call.arguments(),
                                    "callId", call.callId()))
                            .toList());
        }
        if (decision instanceof FinalAnswerDecision finalAnswerDecision) {
            return Map.of(
                    "type", DecisionType.FINAL_ANSWER.name(),
                    "content", finalAnswerDecision.content(),
                    "confidenceScore", finalAnswerDecision.confidenceScore(),
                    "tokenUsage", Map.of(
                            "inputTokens", finalAnswerDecision.tokenUsage().inputTokens(),
                            "outputTokens", finalAnswerDecision.tokenUsage().outputTokens(),
                            "totalTokens", finalAnswerDecision.tokenUsage().totalTokens()));
        }
        if (decision instanceof NoActionDecision noActionDecision) {
            return Map.of(
                    "type", DecisionType.NO_ACTION.name(),
                    "reason", noActionDecision.reason());
        }
        return Map.of();
    }

    private List<Map<String, Object>> serializeMessages(List<Message> messages) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Message message : messages) {
            if (message.role() == MessageRole.SYSTEM) {
                continue;
            }
            serialized.add(Map.of(
                    "role", message.role().name(),
                    "content", message.content() == null ? "" : message.content(),
                    "name", message.name() == null ? "" : message.name(),
                    "timestamp", message.timestamp().toString(),
                    "metadata", message.metadata()));
        }
        return serialized;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
