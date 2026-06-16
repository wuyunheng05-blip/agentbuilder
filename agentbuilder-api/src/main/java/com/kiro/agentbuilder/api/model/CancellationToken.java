package com.kiro.agentbuilder.api.model;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {

    private final AtomicBoolean cancelled;

    public CancellationToken() {
        this(new AtomicBoolean(false));
    }

    private CancellationToken(AtomicBoolean cancelled) {
        this.cancelled = cancelled;
    }

    public CancellationToken child() {
        return new CancellationToken(cancelled);
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
