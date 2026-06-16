package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.CancellationToken;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.TokenBudget;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExecutionContextFactory {

    public ExecutionContext create(AgentConfig config, AgentInput input) {
        return create(config, input, null);
    }

    @SuppressWarnings("unchecked")
    public ExecutionContext create(AgentConfig config, AgentInput input, AgentSnapshot snapshot) {
        List<Message> history = new ArrayList<>();
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            history.add(Message.system(config.systemPrompt()));
        }
        if (snapshot != null && snapshot.snapshotData().get("messages") instanceof List<?> messages) {
            messages.stream()
                    .filter(Message.class::isInstance)
                    .map(Message.class::cast)
                    .forEach(history::add);
        }
        if (input != null && input.content() != null && !input.content().isBlank()) {
            history.add(Message.user(input.content()));
        }
        return new ExecutionContext(
                UUID.randomUUID().toString(),
                config.agentId(),
                input != null && input.sessionId() != null ? input.sessionId() : config.sessionId(),
                history,
                new TokenBudget(config.maxTokens()),
                0,
                new CancellationToken(),
                snapshot == null ? config.attributes() : Map.of("snapshot", snapshot, "config", config),
                Instant.now(),
                config.runTimeout());
    }
}
