package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.tool.JsonSchema;

import java.util.List;
import java.util.Map;

public class JsonSchemaValidator {

    public void validate(JsonSchema schema, Map<String, Object> arguments) {
        if (schema == null || schema.schema().isEmpty()) {
            return;
        }
        Map<String, Object> rawSchema = schema.schema();
        Object required = rawSchema.get("required");
        if (required instanceof List<?> requiredList) {
            for (Object field : requiredList) {
                String fieldName = String.valueOf(field);
                if (!arguments.containsKey(fieldName)) {
                    throw new IllegalArgumentException("Missing required argument: " + fieldName);
                }
            }
        }

        Object properties = rawSchema.get("properties");
        if (properties instanceof Map<?, ?> propertiesMap) {
            for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
                String fieldName = String.valueOf(entry.getKey());
                Object value = arguments.get(fieldName);
                if (value == null || !(entry.getValue() instanceof Map<?, ?> propertySchema)) {
                    continue;
                }
                Object type = propertySchema.get("type");
                if (type != null && !matchesType(String.valueOf(type), value)) {
                    throw new IllegalArgumentException("Argument " + fieldName + " does not match schema type " + type);
                }
            }
        }
    }

    private boolean matchesType(String schemaType, Object value) {
        return switch (schemaType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            default -> true;
        };
    }
}
