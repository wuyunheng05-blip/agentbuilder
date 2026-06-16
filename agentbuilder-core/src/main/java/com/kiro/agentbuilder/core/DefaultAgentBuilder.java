package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.Agent;
import com.kiro.agentbuilder.api.AgentBuilder;
import com.kiro.agentbuilder.api.extension.AgentInterceptor;
import com.kiro.agentbuilder.api.extension.ContextProvider;
import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.memory.MemoryStore;
import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.spi.AuthenticationService;
import com.kiro.agentbuilder.api.spi.AuthorizationService;
import com.kiro.agentbuilder.api.spi.Configuration;
import com.kiro.agentbuilder.api.spi.QuotaService;
import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;
import com.kiro.agentbuilder.api.storage.StorageModule;
import com.kiro.agentbuilder.api.tool.Tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultAgentBuilder implements AgentBuilder {

    private final Configuration configuration;
    private final List<Tool> tools = new ArrayList<>();
    private final List<AgentInterceptor> interceptors = new ArrayList<>();
    private final List<LifecycleHook> hooks = new ArrayList<>();
    private final List<ContextProvider> contextProviders = new ArrayList<>();
    private final List<ExecutionGuard> guards = new ArrayList<>();
    private String agentId = "agent-" + UUID.randomUUID();
    private String systemPrompt = "";
    private ModelProvider modelProvider;
    private StorageModule storageModule;
    private MemoryStore memoryStore;
    private SensitiveDataMasker masker;
    private QuotaService quotaService;
    private AuthenticationService authenticationService;
    private AuthorizationService authorizationService;
    private int maxIterations = 6;
    private int maxRecursionDepth = 2;
    private long maxTokens = 8_000;
    private Duration runTimeout = Duration.ofMinutes(2);
    private String sessionId;

    public DefaultAgentBuilder(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public AgentBuilder id(String agentId) {
        this.agentId = agentId;
        return this;
    }

    @Override
    public AgentBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        return this;
    }

    @Override
    public AgentBuilder tool(Tool tool) {
        if (tool != null) {
            tools.add(tool);
        }
        return this;
    }

    @Override
    public AgentBuilder memory(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        return this;
    }

    @Override
    public AgentBuilder interceptor(AgentInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
        return this;
    }

    @Override
    public AgentBuilder hook(LifecycleHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
        return this;
    }

    @Override
    public AgentBuilder contextProvider(ContextProvider contextProvider) {
        if (contextProvider != null) {
            contextProviders.add(contextProvider);
        }
        return this;
    }

    @Override
    public AgentBuilder guard(ExecutionGuard guard) {
        if (guard != null) {
            guards.add(guard);
        }
        return this;
    }

    @Override
    public AgentBuilder model(ModelProvider provider) {
        this.modelProvider = provider;
        return this;
    }

    @Override
    public AgentBuilder masker(SensitiveDataMasker masker) {
        this.masker = masker;
        return this;
    }

    @Override
    public AgentBuilder maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public AgentBuilder maxRecursionDepth(int maxRecursionDepth) {
        this.maxRecursionDepth = maxRecursionDepth;
        return this;
    }

    @Override
    public AgentBuilder runTimeout(Duration timeout) {
        this.runTimeout = timeout;
        return this;
    }

    @Override
    public AgentBuilder tokenBudget(long maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    @Override
    public AgentBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    @Override
    public AgentConfig preview() {
        StorageModule resolvedStorage = storageModule == null ? configuration.createStorageModule() : storageModule;
        MemoryStore resolvedMemory = memoryStore == null ? resolvedStorage.memoryStore() : memoryStore;
        SensitiveDataMasker resolvedMasker = masker == null ? configuration.createSensitiveDataMasker() : masker;
        QuotaService resolvedQuota = quotaService == null ? configuration.createQuotaService() : quotaService;
        AuthenticationService resolvedAuthentication = authenticationService == null
                ? configuration.createAuthenticationService() : authenticationService;
        AuthorizationService resolvedAuthorization = authorizationService == null
                ? configuration.createAuthorizationService() : authorizationService;
        ModelProvider resolvedModelProvider = modelProvider == null ? configuration.createModelProvider() : modelProvider;

        List<ExecutionGuard> resolvedGuards = new ArrayList<>(guards);
        resolvedGuards.add(new RecursionDepthGuard(maxRecursionDepth));
        resolvedGuards.add(new QuotaGuard(resolvedQuota));
        resolvedGuards.add(new CancellationGuard());

        List<LifecycleHook> resolvedHooks = new ArrayList<>(hooks);
        resolvedHooks.add(new TraceRecordingHook(
                resolvedStorage.observabilityStore(),
                agentId,
                resolvedModelProvider.getModelName(),
                "REACT"));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("agentId", agentId);

        return new AgentConfig(
                agentId,
                systemPrompt,
                tools,
                interceptors,
                resolvedHooks,
                contextProviders,
                resolvedGuards,
                resolvedModelProvider,
                resolvedMemory,
                resolvedStorage,
                resolvedMasker,
                resolvedQuota,
                resolvedAuthentication,
                resolvedAuthorization,
                maxIterations,
                maxRecursionDepth,
                maxTokens,
                runTimeout,
                sessionId,
                attributes);
    }

    @Override
    public Agent build() {
        return new AgentRuntime(preview());
    }
}
