package com.kiro.agentbuilder.test;

import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.ModelEvent;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.model.ModelResponse;
import com.kiro.agentbuilder.api.model.TokenUsage;
import com.kiro.agentbuilder.core.CircuitBreaker;
import com.kiro.agentbuilder.core.CircuitBreakerOpenException;
import com.kiro.agentbuilder.core.CircuitBreakerState;
import com.kiro.agentbuilder.core.RateLimitExceededException;
import com.kiro.agentbuilder.core.RateLimiter;
import com.kiro.agentbuilder.core.ResilienceModelProvider;
import com.kiro.agentbuilder.core.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilienceModelProviderTest {

    @Test
    void shouldRetryTransientModelFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ModelProvider delegate = new ModelProvider() {
            @Override
            public String getModelName() {
                return "retry-model";
            }

            @Override
            public reactor.core.publisher.Flux<ModelEvent> call(ModelRequest request) {
                if (attempts.getAndIncrement() < 2) {
                    return reactor.core.publisher.Flux.error(new IllegalStateException("temporary failure"));
                }
                return reactor.core.publisher.Flux.just(ModelEvent.complete(new ModelResponse(
                        "ok",
                        List.of(),
                        new TokenUsage(1, 1, 2),
                        0.9,
                        Map.of())));
            }
        };

        ResilienceModelProvider provider = new ResilienceModelProvider(
                delegate,
                new CircuitBreaker(5, Duration.ofSeconds(1)),
                new RateLimiter(10, Duration.ofSeconds(1)),
                RetryPolicy.forModelCalls());

        var events = provider.call(sampleRequest()).collectList().block();
        assertEquals(3, attempts.get());
        assertEquals(1, events.size());
        assertEquals("ok", events.get(0).response().content());
    }

    @Test
    void shouldOpenCircuitAfterRepeatedFailures() {
        ModelProvider delegate = new ModelProvider() {
            @Override
            public String getModelName() {
                return "broken-model";
            }

            @Override
            public reactor.core.publisher.Flux<ModelEvent> call(ModelRequest request) {
                return reactor.core.publisher.Flux.error(new IllegalStateException("boom"));
            }
        };

        CircuitBreaker circuitBreaker = new CircuitBreaker(2, Duration.ofSeconds(60));
        ResilienceModelProvider provider = new ResilienceModelProvider(
                delegate,
                circuitBreaker,
                new RateLimiter(10, Duration.ofSeconds(1)),
                new RetryPolicy(0, Duration.ofMillis(1), error -> true));

        assertThrows(IllegalStateException.class, () -> provider.call(sampleRequest()).collectList().block());
        assertThrows(IllegalStateException.class, () -> provider.call(sampleRequest()).collectList().block());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.state());
        Throwable error = assertThrows(Throwable.class, () -> provider.call(sampleRequest()).collectList().block());
        assertTrue(error.getCause() instanceof CircuitBreakerOpenException || error instanceof CircuitBreakerOpenException);
    }

    @Test
    void shouldRejectWhenRateLimitExceeded() {
        ModelProvider delegate = new ModelProvider() {
            @Override
            public String getModelName() {
                return "limited-model";
            }

            @Override
            public reactor.core.publisher.Flux<ModelEvent> call(ModelRequest request) {
                return reactor.core.publisher.Flux.just(ModelEvent.complete(new ModelResponse(
                        "ok",
                        List.of(),
                        new TokenUsage(1, 1, 2),
                        0.9,
                        Map.of())));
            }
        };

        ResilienceModelProvider provider = new ResilienceModelProvider(
                delegate,
                new CircuitBreaker(5, Duration.ofSeconds(1)),
                new RateLimiter(1, Duration.ofDays(1)),
                new RetryPolicy(0, Duration.ofMillis(1), error -> false));

        provider.call(sampleRequest()).collectList().block();
        Throwable error = assertThrows(Throwable.class, () -> provider.call(sampleRequest()).collectList().block());
        assertTrue(error.getCause() instanceof RateLimitExceededException || error instanceof RateLimitExceededException);
    }

    private ModelRequest sampleRequest() {
        return new ModelRequest(
                "demo",
                "system",
                List.of(Message.user("hello")),
                List.of(),
                Map.of());
    }
}
