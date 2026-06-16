package com.kiro.agentbuilder.storage;

import com.kiro.agentbuilder.api.storage.StorageModule;
import com.kiro.agentbuilder.api.storage.StorageModuleFactory;
import com.kiro.agentbuilder.memory.InMemoryMemoryStore;

public class InMemoryStorageModuleFactory implements StorageModuleFactory {

    @Override
    public String getName() {
        return "in-memory";
    }

    @Override
    public StorageModule create() {
        return new StorageModule(
                new InMemoryMemoryStore(),
                new InMemorySnapshotStore(),
                new InMemoryObservabilityStore(),
                new InMemoryOutboxStore());
    }
}
