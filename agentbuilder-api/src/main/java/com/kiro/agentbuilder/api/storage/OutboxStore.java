package com.kiro.agentbuilder.api.storage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxStore {

    Mono<Void> publish(OutboxEvent event);

    Flux<OutboxEvent> pollPending(int maxBatch);

    Mono<Void> markPublished(String eventId);
}
