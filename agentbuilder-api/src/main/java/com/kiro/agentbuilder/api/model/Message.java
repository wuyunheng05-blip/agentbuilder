package com.kiro.agentbuilder.api.model;

import java.time.Instant;
import java.util.Map;

public record Message(
        MessageRole role,
        String content,
        String name,
        Instant timestamp,
        Map<String, Object> metadata) {

    public Message {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, content, null, Instant.now(), Map.of());
    }

    public static Message user(String content) {
        return new Message(MessageRole.USER, content, null, Instant.now(), Map.of());
    }

    public static Message assistant(String content) {
        return new Message(MessageRole.ASSISTANT, content, null, Instant.now(), Map.of());
    }

    public static Message tool(String toolName, String content) {
        return new Message(MessageRole.TOOL, content, toolName, Instant.now(), Map.of());
    }
}
