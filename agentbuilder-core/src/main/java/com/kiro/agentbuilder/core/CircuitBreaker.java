package com.kiro.agentbuilder.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        this(failureThreshold, openDuration, Clock.systemUTC());
    }

    public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofSeconds(30) : openDuration;
        this.clock = clock;
    }

    public CircuitBreakerState state() {
        refreshStateIfNeeded();
        return state.get();
    }

    public <T> Mono<T> protectMono(Supplier<Mono<T>> supplier) {
        return Mono.defer(() -> {
            if (!tryAcquire()) {
                return Mono.error(new CircuitBreakerOpenException("Circuit breaker is open"));
            }
            return supplier.get()
                    .doOnSuccess(ignored -> recordSuccess())
                    .doOnError(error -> recordFailure());
        });
    }

    public <T> Flux<T> protectFlux(Supplier<Flux<T>> supplier) {
        return Flux.defer(() -> {
            if (!tryAcquire()) {
                return Flux.error(new CircuitBreakerOpenException("Circuit breaker is open"));
            }
            return supplier.get()
                    .doOnComplete(this::recordSuccess)
                    .doOnError(error -> recordFailure());
        });
    }

    private boolean tryAcquire() {
        refreshStateIfNeeded();
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.OPEN) {
            return false;
        }
        if (current == CircuitBreakerState.HALF_OPEN) {
            return true;
        }
        return true;
    }

    private void refreshStateIfNeeded() {
        if (state.get() == CircuitBreakerState.OPEN) {
            Instant opened = openedAt.get();
            if (opened != null && clock.instant().isAfter(opened.plus(openDuration))) {
                state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN);
            }
        }
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(CircuitBreakerState.CLOSED);
        openedAt.set(null);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == CircuitBreakerState.HALF_OPEN || failures >= failureThreshold) {
            state.set(CircuitBreakerState.OPEN);
            openedAt.set(clock.instant());
        }
    }
}
