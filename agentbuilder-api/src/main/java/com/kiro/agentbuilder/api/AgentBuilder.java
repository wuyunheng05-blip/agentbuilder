package com.kiro.agentbuilder.api;

import com.kiro.agentbuilder.api.extension.AgentInterceptor;
import com.kiro.agentbuilder.api.extension.ContextProvider;
import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.memory.MemoryStore;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;
import com.kiro.agentbuilder.api.tool.Tool;

import java.time.Duration;

public interface AgentBuilder {

    AgentBuilder id(String agentId);

    AgentBuilder systemPrompt(String systemPrompt);

    AgentBuilder tool(Tool tool);

    AgentBuilder memory(MemoryStore memoryStore);

    AgentBuilder interceptor(AgentInterceptor interceptor);

    AgentBuilder hook(LifecycleHook hook);

    AgentBuilder contextProvider(ContextProvider contextProvider);

    AgentBuilder guard(ExecutionGuard guard);

    AgentBuilder model(ModelProvider provider);

    AgentBuilder masker(SensitiveDataMasker masker);

    AgentBuilder maxIterations(int maxIterations);

    AgentBuilder maxRecursionDepth(int maxRecursionDepth);

    AgentBuilder runTimeout(Duration timeout);

    AgentBuilder tokenBudget(long maxTokens);

    AgentBuilder sessionId(String sessionId);

    AgentConfig preview();

    Agent build();
}
