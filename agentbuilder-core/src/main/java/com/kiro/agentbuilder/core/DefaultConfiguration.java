package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.spi.AuthenticationService;
import com.kiro.agentbuilder.api.spi.AuthorizationService;
import com.kiro.agentbuilder.api.spi.Configuration;
import com.kiro.agentbuilder.api.spi.QuotaService;
import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;
import com.kiro.agentbuilder.api.storage.StorageModule;

public class DefaultConfiguration implements Configuration {

    @Override
    public ModelProvider createModelProvider() {
        return new UnsupportedModelProvider();
    }

    @Override
    public StorageModule createStorageModule() {
        return StorageModule.inMemory();
    }

    @Override
    public QuotaService createQuotaService() {
        return new NoopQuotaService();
    }

    @Override
    public AuthenticationService createAuthenticationService() {
        return new NoopAuthenticationService();
    }

    @Override
    public AuthorizationService createAuthorizationService() {
        return new NoopAuthorizationService();
    }

    @Override
    public SensitiveDataMasker createSensitiveDataMasker() {
        return new RegexSensitiveDataMasker();
    }
}
