package com.kiro.agentbuilder.api.tool;

public record ToolDefinition(String name, String description, JsonSchema parameterSchema, RiskLevel riskLevel) {
}
