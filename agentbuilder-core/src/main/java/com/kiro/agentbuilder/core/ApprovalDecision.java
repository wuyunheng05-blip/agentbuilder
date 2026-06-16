package com.kiro.agentbuilder.core;

public record ApprovalDecision(boolean approved, String reason) {

    public static ApprovalDecision approved(String reason) {
        return new ApprovalDecision(true, reason);
    }

    public static ApprovalDecision rejected(String reason) {
        return new ApprovalDecision(false, reason);
    }
}
