package com.kiro.agentbuilder.api.tool;

import java.util.Map;

public record JsonSchema(Map<String, Object> schema) {

    public JsonSchema {
        schema = schema == null ? Map.of() : Map.copyOf(schema);
    }

    public static JsonSchema empty() {
        return new JsonSchema(Map.of());
    }
}
