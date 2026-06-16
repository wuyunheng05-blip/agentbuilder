package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.MessageRole;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;

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
        snapshotData.put("iterationAttempts", context.attributes().getOrDefault(RuntimeAttributes.ITERATION_ATTEMPTS, 0));
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

    public String restoreRunId(AgentSnapshot snapshot) {
        Object runId = snapshot.snapshotData().get("runId");
        return runId == null ? null : String.valueOf(runId);
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
}
