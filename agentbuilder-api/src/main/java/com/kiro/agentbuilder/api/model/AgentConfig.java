package com.kiro.agentbuilder.api.model;

import com.kiro.agentbuilder.api.extension.AgentInterceptor;
import com.kiro.agentbuilder.api.extension.ContextProvider;
import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.memory.MemoryStore;
import com.kiro.agentbuilder.api.spi.AuthenticationService;
import com.kiro.agentbuilder.api.spi.AuthorizationService;
import com.kiro.agentbuilder.api.spi.QuotaService;
import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;
import com.kiro.agentbuilder.api.storage.StorageModule;
import com.kiro.agentbuilder.api.tool.Tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record AgentConfig(
        String agentId,
        String systemPrompt,
        List<Tool> tools,
        List<AgentInterceptor> interceptors,
        List<LifecycleHook> hooks,
        List<ContextProvider> contextProviders,
        List<ExecutionGuard> guards,
        ModelProvider modelProvider,
        MemoryStore memoryStore,
        StorageModule storageModule,
        SensitiveDataMasker sensitiveDataMasker,
        QuotaService quotaService,
        AuthenticationService authenticationService,
        AuthorizationService authorizationService,
        int maxIterations,
        int maxRecursionDepth,
        long maxTokens,
        Duration runTimeout,
        String sessionId,
        Map<String, Object> attributes) {

    public AgentConfig {
        tools = List.copyOf(tools);
        interceptors = List.copyOf(interceptors);
        hooks = List.copyOf(hooks);
        contextProviders = List.copyOf(contextProviders);
        guards = List.copyOf(guards);
        attributes = Map.copyOf(attributes);
    }
}
