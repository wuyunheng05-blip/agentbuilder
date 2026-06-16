package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.tool.ToolCall;

public class HitlPauseException extends RuntimeException {

    private final String approvalRequestId;
    private final ToolCall toolCall;

    public HitlPauseException(String message, String approvalRequestId, ToolCall toolCall) {
        super(message);
        this.approvalRequestId = approvalRequestId;
        this.toolCall = toolCall;
    }

    public String approvalRequestId() {
        return approvalRequestId;
    }

    public ToolCall toolCall() {
        return toolCall;
    }
}
