package com.kiro.agentbuilder.api.extension;

public record GuardResult(boolean allowed, String rejectReason) {

    public static GuardResult allow() {
        return new GuardResult(true, null);
    }

    public static GuardResult reject(String reason) {
        return new GuardResult(false, reason);
    }
}
