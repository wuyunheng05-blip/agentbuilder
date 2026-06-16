package com.kiro.agentbuilder.core;

import java.time.Duration;

public record PolicyResult(boolean allowed, String reason, Duration timeout) {

    public static PolicyResult allow(Duration timeout) {
        return new PolicyResult(true, "allowed", timeout);
    }

    public static PolicyResult reject(String reason) {
        return new PolicyResult(false, reason, Duration.ZERO);
    }
}
