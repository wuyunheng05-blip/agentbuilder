package com.kiro.agentbuilder.api.tool;

import java.util.Map;
import java.util.UUID;

public record ToolCall(String toolName, Map<String, Object> arguments, String callId) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        callId = callId == null ? UUID.randomUUID().toString() : callId;
    }
}
