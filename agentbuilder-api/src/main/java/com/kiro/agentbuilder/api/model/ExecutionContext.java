package com.kiro.agentbuilder.api.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExecutionContext {

    private final String runId;
    private final String agentId;
    private final String sessionId;
    private final List<Message> messageHistory;
    private final TokenBudget tokenBudget;
    private final int recursionDepth;
    private final CancellationToken cancellationToken;
    private final Map<String, Object> attributes;
    private final Instant startTime;
    private final Duration timeout;

    public ExecutionContext(
            String runId,
            String agentId,
            String sessionId,
            List<Message> messageHistory,
            TokenBudget tokenBudget,
            int recursionDepth,
            CancellationToken cancellationToken,
            Map<String, Object> attributes,
            Instant startTime,
            Duration timeout) {
        this.runId = runId == null ? UUID.randomUUID().toString() : runId;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.messageHistory = new CopyOnWriteArrayList<>(messageHistory);
        this.tokenBudget = tokenBudget;
        this.recursionDepth = recursionDepth;
        this.cancellationToken = cancellationToken;
        this.attributes = new ConcurrentHashMap<>(attributes);
        this.startTime = startTime == null ? Instant.now() : startTime;
        this.timeout = timeout == null ? Duration.ofMinutes(2) : timeout;
    }

    public String runId() {
        return runId;
    }

    public String agentId() {
        return agentId;
    }

    public String sessionId() {
        return sessionId;
    }

    public List<Message> messageHistory() {
        return List.copyOf(messageHistory);
    }

    public void addMessage(Message message) {
        messageHistory.add(message);
    }

    public TokenBudget tokenBudget() {
        return tokenBudget;
    }

    public int recursionDepth() {
        return recursionDepth;
    }

    public CancellationToken cancellationToken() {
        return cancellationToken;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Instant startTime() {
        return startTime;
    }

    public Duration timeout() {
        return timeout;
    }

    public boolean isTimedOut() {
        return Instant.now().isAfter(startTime.plus(timeout));
    }

    public ExecutionContext nested() {
        return new ExecutionContext(
                null,
                agentId,
                sessionId,
                new ArrayList<>(messageHistory),
                tokenBudget,
                recursionDepth + 1,
                cancellationToken.child(),
                new ConcurrentHashMap<>(attributes),
                Instant.now(),
                timeout);
    }
}
