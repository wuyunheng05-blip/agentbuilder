package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.storage.ApprovalStatus;

public record ApprovalDecision(
        ApprovalStatus status,
        String reason,
        String approvalRequestId) {

    public static ApprovalDecision approved(String reason, String approvalRequestId) {
        return new ApprovalDecision(ApprovalStatus.APPROVED, reason, approvalRequestId);
    }

    public static ApprovalDecision rejected(String reason, String approvalRequestId) {
        return new ApprovalDecision(ApprovalStatus.REJECTED, reason, approvalRequestId);
    }

    public static ApprovalDecision pending(String reason, String approvalRequestId) {
        return new ApprovalDecision(ApprovalStatus.PENDING, reason, approvalRequestId);
    }

    public boolean approved() {
        return status == ApprovalStatus.APPROVED;
    }

    public boolean rejected() {
        return status == ApprovalStatus.REJECTED;
    }

    public boolean pending() {
        return status == ApprovalStatus.PENDING;
    }
}
