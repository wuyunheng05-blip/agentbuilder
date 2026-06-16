package com.kiro.agentbuilder.api.storage;

import java.util.ServiceLoader;

public record StorageModule(
        com.kiro.agentbuilder.api.memory.MemoryStore memoryStore,
        SnapshotStore snapshotStore,
        ObservabilityStore observabilityStore,
        OutboxStore outboxStore,
        ApprovalStore approvalStore) {

    public static StorageModule inMemory() {
        return create("in-memory");
    }

    public static StorageModule create(String factoryName) {
        return ServiceLoader.load(StorageModuleFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(factory -> factory.getName().equals(factoryName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No StorageModuleFactory found for " + factoryName))
                .create();
    }
}
