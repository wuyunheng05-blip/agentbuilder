package com.kiro.agentbuilder.api.spi;

import reactor.core.publisher.Mono;

import java.security.Principal;

public interface AuthenticationService {

    Mono<Principal> authenticate(AuthCredentials credentials);
}
