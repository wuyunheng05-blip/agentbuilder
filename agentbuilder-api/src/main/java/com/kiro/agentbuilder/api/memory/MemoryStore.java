package com.kiro.agentbuilder.api.memory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MemoryStore {

    Mono<Void> save(MemoryEntry entry);

    Flux<MemoryEntry> query(MemoryQuery query);

    Mono<Void> delete(String entryId);
}
