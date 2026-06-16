package com.kiro.agentbuilder.api.spi;

import reactor.core.publisher.Mono;

public interface QuotaService {

    Mono<Boolean> checkAndConsume(String agentId, QuotaType type, long amount);
}
