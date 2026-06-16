package com.kiro.agentbuilder.api.spi;

public interface SensitiveDataMasker {

    String mask(String input);
}
