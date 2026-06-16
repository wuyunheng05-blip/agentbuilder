package com.kiro.agentbuilder.api.tool;

import java.util.Map;

public record ToolResult(String callId, Object output, boolean error, Map<String, Object> metadata) {

    public ToolResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolResult success(String callId, Object output) {
        return new ToolResult(callId, output, false, Map.of());
    }

    public static ToolResult error(String callId, String message) {
        return new ToolResult(callId, message, true, Map.of("message", message));
    }
}
