package com.kiro.agentbuilder.api.storage;

import reactor.core.publisher.Mono;

public interface SnapshotStore {

    Mono<Void> save(AgentSnapshot snapshot);

    Mono<AgentSnapshot> load(String snapshotId);

    Mono<AgentSnapshot> findLatest(String sessionId);
}
