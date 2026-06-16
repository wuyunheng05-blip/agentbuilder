package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.AgentBuilder;
import com.kiro.agentbuilder.api.spi.Configuration;

import java.util.ServiceLoader;

public final class AgentBuilders {

    private AgentBuilders() {
    }

    public static AgentBuilder builder() {
        return new DefaultAgentBuilder(loadConfiguration());
    }

    public static Configuration loadConfiguration() {
        return ServiceLoader.load(Configuration.class)
                .findFirst()
                .orElseGet(DefaultConfiguration::new);
    }
}
