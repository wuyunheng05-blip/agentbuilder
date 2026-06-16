package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.ModelRequest;
import com.kiro.agentbuilder.api.tool.ToolDefinition;
import com.kiro.agentbuilder.memory.ContextWindowManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PromptComposer {

    private final AgentConfig config;
    private final ContextWindowManager contextWindowManager;

    public PromptComposer(AgentConfig config, ContextWindowManager contextWindowManager) {
        this.config = config;
        this.contextWindowManager = contextWindowManager;
    }

    public ModelRequest compose(ExecutionContext context, List<ToolDefinition> toolDefinitions, int iteration) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentId", config.agentId());
        metadata.put("runId", context.runId());
        metadata.put("iteration", iteration);
        return new ModelRequest(
                config.modelProvider().getModelName(),
                config.systemPrompt(),
                contextWindowManager.trim(context.messageHistory()),
                toolDefinitions,
                metadata);
    }
}
