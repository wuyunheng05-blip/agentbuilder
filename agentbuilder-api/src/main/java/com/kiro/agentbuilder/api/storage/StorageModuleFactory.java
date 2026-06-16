package com.kiro.agentbuilder.api.storage;

public interface StorageModuleFactory {

    String getName();

    StorageModule create();
}
