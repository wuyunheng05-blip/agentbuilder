package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.spi.AuthCredentials;
import com.kiro.agentbuilder.api.spi.AuthenticationService;
import reactor.core.publisher.Mono;

import java.security.Principal;

public class NoopAuthenticationService implements AuthenticationService {

    @Override
    public Mono<Principal> authenticate(AuthCredentials credentials) {
        String name = credentials == null || credentials.userId() == null ? "anonymous" : credentials.userId();
        return Mono.just((Principal) () -> name);
    }
}
