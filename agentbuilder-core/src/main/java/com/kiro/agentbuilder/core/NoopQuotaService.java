package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.spi.QuotaService;
import com.kiro.agentbuilder.api.spi.QuotaType;
import reactor.core.publisher.Mono;

public class NoopQuotaService implements QuotaService {

    @Override
    public Mono<Boolean> checkAndConsume(String agentId, QuotaType type, long amount) {
        return Mono.just(Boolean.TRUE);
    }
}
