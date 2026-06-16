package com.kiro.agentbuilder.api.model;

import java.util.concurrent.atomic.AtomicLong;

public final class TokenBudget {

    private final long totalBudget;
    private final AtomicLong consumed = new AtomicLong();

    public TokenBudget(long totalBudget) {
        this.totalBudget = Math.max(0, totalBudget);
    }

    public long totalBudget() {
        return totalBudget;
    }

    public long consumed() {
        return consumed.get();
    }

    public long remaining() {
        return Math.max(0, totalBudget - consumed.get());
    }

    public void consume(long tokens) {
        if (tokens > 0) {
            consumed.addAndGet(tokens);
        }
    }
}
