package com.kiro.agentbuilder.api.model;

import com.kiro.agentbuilder.api.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

public record ModelRequest(
        String model,
        String systemPrompt,
        List<Message> messages,
        List<ToolDefinition> tools,
        Map<String, Object> metadata) {

    public ModelRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
