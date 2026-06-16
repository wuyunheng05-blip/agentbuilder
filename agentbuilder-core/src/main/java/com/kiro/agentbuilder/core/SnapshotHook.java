package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.storage.SnapshotStore;
import com.kiro.agentbuilder.api.storage.StepTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SnapshotHook implements LifecycleHook {

    private final SnapshotStore snapshotStore;
    private final ExecutionSnapshotMapper snapshotMapper;
    private final int stepInterval;
    private final Map<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    public SnapshotHook(SnapshotStore snapshotStore, ExecutionSnapshotMapper snapshotMapper, int stepInterval) {
        this.snapshotStore = snapshotStore;
        this.snapshotMapper = snapshotMapper;
        this.stepInterval = Math.max(1, stepInterval);
    }

    @Override
    public void onStart(ExecutionContext context, AgentInput input) {
        stepCounters.put(context.runId(), new AtomicInteger());
        saveSnapshot(context, "RUN_START");
    }

    @Override
    public void onStep(ExecutionContext context, StepTrace stepTrace) {
        if (stepCounters.computeIfAbsent(context.runId(), ignored -> new AtomicInteger()).incrementAndGet() % stepInterval == 0) {
            saveSnapshot(context, "STEP");
        }
    }

    @Override
    public void onError(ExecutionContext context, Throwable error) {
        saveSnapshot(context, error instanceof HitlPauseException ? "APPROVAL_REQUIRED" : "ERROR");
    }

    @Override
    public void onComplete(ExecutionContext context, FinalPayload payload) {
        saveSnapshot(context, "COMPLETE");
        stepCounters.remove(context.runId());
    }

    @Override
    public void onCleanup(ExecutionContext context) {
        stepCounters.remove(context.runId());
    }

    private void saveSnapshot(ExecutionContext context, String triggerType) {
        AgentSnapshot snapshot = snapshotMapper.toSnapshot(context, triggerType);
        snapshotStore.save(snapshot).block();
        context.attributes().put(SnapshotAttributes.LATEST_SNAPSHOT_ID, snapshot.snapshotId());
    }
}
