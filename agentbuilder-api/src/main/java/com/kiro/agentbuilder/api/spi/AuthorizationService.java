package com.kiro.agentbuilder.api.spi;

import reactor.core.publisher.Mono;

import java.security.Principal;

public interface AuthorizationService {

    Mono<Boolean> authorize(Principal principal, String resource, String action);
}
