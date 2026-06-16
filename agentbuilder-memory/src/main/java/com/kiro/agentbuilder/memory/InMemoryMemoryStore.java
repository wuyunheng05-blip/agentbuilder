package com.kiro.agentbuilder.memory;

import com.kiro.agentbuilder.api.memory.MemoryEntry;
import com.kiro.agentbuilder.api.memory.MemoryQuery;
import com.kiro.agentbuilder.api.memory.MemoryStore;
import com.kiro.agentbuilder.api.memory.MemoryType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryStore implements MemoryStore {

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(MemoryEntry entry) {
        entries.put(entry.id(), entry);
        return Mono.empty();
    }

    @Override
    public Flux<MemoryEntry> query(MemoryQuery query) {
        return Flux.fromStream(entries.values().stream()
                .filter(entry -> query.agentInstanceId() == null || query.agentInstanceId().equals(entry.agentInstanceId()))
                .filter(entry -> query.type() == null || query.type() == entry.type())
                .filter(entry -> entry.importanceScore() >= query.minImportance())
                .filter(entry -> matchesText(query.textQuery(), entry))
                .sorted(sorter(query.type()))
                .limit(query.topK()));
    }

    @Override
    public Mono<Void> delete(String entryId) {
        entries.remove(entryId);
        return Mono.empty();
    }

    private boolean matchesText(String textQuery, MemoryEntry entry) {
        if (textQuery == null || textQuery.isBlank()) {
            return true;
        }
        return entry.content() != null && entry.content().contains(textQuery);
    }

    private Comparator<MemoryEntry> sorter(MemoryType type) {
        Comparator<MemoryEntry> base = Comparator.comparingDouble(MemoryEntry::importanceScore).reversed()
                .thenComparing(MemoryEntry::createdAt).reversed();
        if (type == MemoryType.WORKING) {
            return Comparator.comparing(MemoryEntry::createdAt).reversed();
        }
        return base;
    }
}
