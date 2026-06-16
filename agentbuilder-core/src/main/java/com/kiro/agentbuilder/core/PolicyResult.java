package com.kiro.agentbuilder.core;

import java.time.Duration;

public record PolicyResult(
        boolean allowed,
        boolean paused,
        String reason,
        Duration timeout,
        String approvalRequestId) {

    public static PolicyResult allow(Duration timeout) {
        return new PolicyResult(true, false, "allowed", timeout, null);
    }

    public static PolicyResult reject(String reason) {
        return new PolicyResult(false, false, reason, Duration.ZERO, null);
    }

    public static PolicyResult pause(String reason, String approvalRequestId) {
        return new PolicyResult(false, true, reason, Duration.ZERO, approvalRequestId);
    }
}
