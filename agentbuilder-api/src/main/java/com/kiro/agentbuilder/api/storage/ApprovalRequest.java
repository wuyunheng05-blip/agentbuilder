package com.kiro.agentbuilder.api.storage;

import com.kiro.agentbuilder.api.tool.ToolCall;

import java.time.Instant;

public record ApprovalRequest(
        String approvalRequestId,
        String sessionId,
        String runId,
        String agentId,
        String snapshotId,
        ToolCall toolCall,
        ApprovalStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt) {

    public ApprovalRequest {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public ApprovalRequest withDecision(ApprovalStatus nextStatus, String nextReason) {
        return new ApprovalRequest(
                approvalRequestId,
                sessionId,
                runId,
                agentId,
                snapshotId,
                toolCall,
                nextStatus,
                nextReason,
                createdAt,
                Instant.now());
    }
}
