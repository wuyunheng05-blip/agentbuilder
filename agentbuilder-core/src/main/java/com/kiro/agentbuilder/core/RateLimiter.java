package com.kiro.agentbuilder.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RateLimiter {

    private final long capacity;
    private final double refillPerMillis;
    private final Clock clock;
    private double availableTokens;
    private Instant lastRefillAt;

    public RateLimiter(long capacity, Duration refillPeriod) {
        this(capacity, refillPeriod, Clock.systemUTC());
    }

    public RateLimiter(long capacity, Duration refillPeriod, Clock clock) {
        this.capacity = Math.max(1, capacity);
        this.refillPerMillis = (double) this.capacity / Math.max(1, refillPeriod.toMillis());
        this.clock = clock;
        this.availableTokens = this.capacity;
        this.lastRefillAt = clock.instant();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1.0d) {
            availableTokens -= 1.0d;
            return true;
        }
        return false;
    }

    private void refill() {
        Instant now = clock.instant();
        long millis = Math.max(0, Duration.between(lastRefillAt, now).toMillis());
        if (millis > 0) {
            availableTokens = Math.min(capacity, availableTokens + millis * refillPerMillis);
            lastRefillAt = now;
        }
    }
}
