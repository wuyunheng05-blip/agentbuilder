package com.kiro.agentbuilder.storage;

import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.storage.SnapshotStore;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, AgentSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(AgentSnapshot snapshot) {
        snapshots.put(snapshot.snapshotId(), snapshot);
        return Mono.empty();
    }

    @Override
    public Mono<AgentSnapshot> load(String snapshotId) {
        return Mono.justOrEmpty(snapshots.get(snapshotId));
    }

    @Override
    public Mono<AgentSnapshot> findLatest(String sessionId) {
        return Mono.justOrEmpty(snapshots.values().stream()
                .filter(snapshot -> sessionId == null || sessionId.equals(snapshot.sessionId()))
                .max(java.util.Comparator.comparing(AgentSnapshot::createdAt)));
    }
}
