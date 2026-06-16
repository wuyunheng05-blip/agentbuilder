package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.CancellationToken;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.TokenBudget;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExecutionContextFactory {

    private final ExecutionSnapshotMapper snapshotMapper;

    public ExecutionContextFactory() {
        this(new ExecutionSnapshotMapper());
    }

    public ExecutionContextFactory(ExecutionSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    public ExecutionContext create(AgentConfig config, AgentInput input) {
        return create(config, input, null);
    }

    public ExecutionContext create(AgentConfig config, AgentInput input, AgentSnapshot snapshot) {
        List<Message> history = new ArrayList<>();
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            history.add(Message.system(config.systemPrompt()));
        }
        if (snapshot != null) {
            history.addAll(snapshotMapper.restoreMessages(snapshot));
        }
        if (input != null && input.content() != null && !input.content().isBlank()) {
            history.add(Message.user(input.content()));
        }
        long totalBudget = snapshot == null ? config.maxTokens() : snapshotMapper.restoreTotalBudget(snapshot, config.maxTokens());
        TokenBudget tokenBudget = new TokenBudget(totalBudget);
        if (snapshot != null) {
            tokenBudget.consume(snapshotMapper.restoreConsumedBudget(snapshot));
        }
        Map<String, Object> attributes = new java.util.HashMap<>(config.attributes());
        if (snapshot != null) {
            attributes.put("snapshot", snapshot);
            attributes.put("config", config);
            attributes.put(SnapshotAttributes.RESUMED_FROM_SNAPSHOT_ID, snapshot.snapshotId());
            attributes.put(RuntimeAttributes.CURRENT_ITERATION, snapshotMapper.restoreCurrentIteration(snapshot));
            attributes.put(RuntimeAttributes.ITERATION_ATTEMPTS, snapshotMapper.restoreIterationAttempts(snapshot));
            attributes.put(RuntimeAttributes.CURRENT_PHASE_INDEX, snapshotMapper.restoreCurrentPhaseIndex(snapshot));
            attributes.put(RuntimeAttributes.CURRENT_DECISION, snapshotMapper.restoreDecision(snapshot));
            if (snapshotMapper.restorePendingApprovalRequestId(snapshot) != null) {
                attributes.put(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID, snapshotMapper.restorePendingApprovalRequestId(snapshot));
            }
        }
        return new ExecutionContext(
                snapshot == null ? UUID.randomUUID().toString() : snapshotMapper.restoreRunId(snapshot),
                config.agentId(),
                input != null && input.sessionId() != null ? input.sessionId() : config.sessionId(),
                history,
                tokenBudget,
                snapshot == null ? 0 : snapshotMapper.restoreRecursionDepth(snapshot),
                new CancellationToken(),
                attributes,
                Instant.now(),
                config.runTimeout());
    }
}
