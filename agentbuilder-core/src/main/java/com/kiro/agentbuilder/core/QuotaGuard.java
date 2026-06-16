package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.ExecutionGuard;
import com.kiro.agentbuilder.api.extension.GuardResult;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.spi.QuotaService;
import com.kiro.agentbuilder.api.spi.QuotaType;
import reactor.core.publisher.Mono;

public class QuotaGuard implements ExecutionGuard {

    private final QuotaService quotaService;

    public QuotaGuard(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public Mono<GuardResult> check(ExecutionContext context) {
        return quotaService.checkAndConsume(context.agentId(), QuotaType.REQUEST, 1)
                .map(allowed -> allowed ? GuardResult.allow() : GuardResult.reject("Quota exceeded"));
    }
}
