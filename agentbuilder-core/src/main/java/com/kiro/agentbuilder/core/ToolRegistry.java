package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.spi.ToolProvider;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> initialTools) {
        initialTools.forEach(this::register);
        ServiceLoader.load(ToolProvider.class).forEach(this::registerProvider);
    }

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public void registerProvider(ToolProvider provider) {
        provider.getTools().forEach(this::register);
    }

    public Optional<Tool> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    public List<ToolDefinition> getDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        tools.values().forEach(tool -> definitions.add(new ToolDefinition(
                tool.getName(),
                tool.getDescription(),
                tool.getParameterSchema(),
                tool.getRiskLevel())));
        return definitions;
    }
}
