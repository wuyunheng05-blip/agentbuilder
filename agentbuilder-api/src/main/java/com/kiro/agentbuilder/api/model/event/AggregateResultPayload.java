package com.kiro.agentbuilder.api.model.event;

import java.util.List;

public record AggregateResultPayload(List<String> candidates, String selected, double confidenceScore) {

    public AggregateResultPayload {
        candidates = List.copyOf(candidates);
    }
}
