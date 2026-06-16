package com.kiro.agentbuilder.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RetryPolicy {

    private final int maxRetries;
    private final Duration firstBackoff;
    private final Predicate<Throwable> retryable;

    public RetryPolicy(int maxRetries, Duration firstBackoff, Predicate<Throwable> retryable) {
        this.maxRetries = Math.max(0, maxRetries);
        this.firstBackoff = firstBackoff == null ? Duration.ofMillis(100) : firstBackoff;
        this.retryable = Objects.requireNonNullElseGet(retryable, () -> error -> true);
    }

    public static RetryPolicy forModelCalls() {
        return new RetryPolicy(
                3,
                Duration.ofMillis(100),
                error -> !(error instanceof CircuitBreakerOpenException) && !(error instanceof RateLimitExceededException));
    }

    public static RetryPolicy forToolCalls() {
        return new RetryPolicy(
                2,
                Duration.ofMillis(50),
                error -> !(error instanceof IllegalArgumentException));
    }

    public <T> Mono<T> executeMono(Supplier<Mono<T>> supplier) {
        return Mono.defer(supplier)
                .retryWhen(Retry.backoff(maxRetries, firstBackoff).filter(retryable));
    }

    public <T> Flux<T> executeFlux(Supplier<Flux<T>> supplier) {
        return Flux.defer(supplier)
                .retryWhen(Retry.backoff(maxRetries, firstBackoff).filter(retryable));
    }
}
