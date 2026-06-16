package com.kiro.agentbuilder.api.model.event;

import com.kiro.agentbuilder.api.tool.ToolCall;

public record ApprovalRequiredPayload(
        String approvalRequestId,
        String snapshotId,
        ToolCall toolCall,
        String message) {
}
