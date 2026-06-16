package com.kiro.agentbuilder.api.spi;

import com.kiro.agentbuilder.api.model.ModelProvider;
import com.kiro.agentbuilder.api.storage.StorageModule;

public interface Configuration {

    ModelProvider createModelProvider();

    StorageModule createStorageModule();

    QuotaService createQuotaService();

    AuthenticationService createAuthenticationService();

    AuthorizationService createAuthorizationService();

    SensitiveDataMasker createSensitiveDataMasker();
}
