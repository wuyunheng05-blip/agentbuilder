package com.kiro.agentbuilder.api.spi;

import java.util.Map;

public record AuthCredentials(String userId, String token, Map<String, Object> attributes) {

    public AuthCredentials {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
