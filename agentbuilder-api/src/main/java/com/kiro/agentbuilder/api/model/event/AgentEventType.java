package com.kiro.agentbuilder.api.model.event;

public enum AgentEventType {
    RUN_START,
    THINKING_DELTA,
    CONTENT_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    MEMORY_RETRIEVED,
    AGGREGATE_RESULT,
    FINAL,
    ERROR
}
