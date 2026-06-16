package com.kiro.agentbuilder.api.memory;

public record MemoryQuery(
        String agentInstanceId,
        MemoryType type,
        String textQuery,
        float[] queryEmbedding,
        int topK,
        double minImportance) {

    public MemoryQuery {
        queryEmbedding = queryEmbedding == null ? null : queryEmbedding.clone();
        topK = topK <= 0 ? 10 : topK;
    }
}
