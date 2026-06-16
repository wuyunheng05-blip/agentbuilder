package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import reactor.core.publisher.Flux;

import java.util.Objects;

public class ResilienceModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final RetryPolicy retryPolicy;

    public ResilienceModelProvider(
            ModelProvider delegate,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter,
            RetryPolicy retryPolicy) {
        this.delegate = Objects.requireNonNull(delegate);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public Flux<ModelEvent> call(ModelRequest request) {
        return retryPolicy.executeFlux(() -> circuitBreaker.protectFlux(() -> {
            if (!rateLimiter.tryAcquire()) {
                return Flux.error(new RateLimitExceededException("Model call rate limit exceeded"));
            }
            return delegate.call(request);
        }));
    }
}
