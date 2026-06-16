package com.kiro.agentbuilder.storage;

import com.kiro.agentbuilder.api.storage.OutboxEvent;
import com.kiro.agentbuilder.api.storage.OutboxStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOutboxStore implements OutboxStore {

    private final Map<String, OutboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> publish(OutboxEvent event) {
        events.put(event.eventId(), event);
        return Mono.empty();
    }

    @Override
    public Flux<OutboxEvent> pollPending(int maxBatch) {
        return Flux.fromStream(events.values().stream()
                .filter(event -> event.publishedAt() == null)
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(maxBatch));
    }

    @Override
    public Mono<Void> markPublished(String eventId) {
        OutboxEvent event = events.get(eventId);
        if (event != null) {
            events.put(eventId, new OutboxEvent(
                    event.eventId(),
                    event.eventType(),
                    event.payload(),
                    event.retryCount(),
                    event.createdAt(),
                    Instant.now()));
        }
        return Mono.empty();
    }
}
