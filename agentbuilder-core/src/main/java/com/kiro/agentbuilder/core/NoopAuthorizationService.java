package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.spi.AuthorizationService;
import reactor.core.publisher.Mono;

import java.security.Principal;

public class NoopAuthorizationService implements AuthorizationService {

    @Override
    public Mono<Boolean> authorize(Principal principal, String resource, String action) {
        return Mono.just(Boolean.TRUE);
    }
}
