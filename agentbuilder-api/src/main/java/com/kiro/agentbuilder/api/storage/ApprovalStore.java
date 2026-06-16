package com.kiro.agentbuilder.api.storage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApprovalStore {

    Mono<Void> save(ApprovalRequest request);

    Mono<ApprovalRequest> get(String approvalRequestId);

    Mono<ApprovalRequest> resolve(String approvalRequestId, ApprovalStatus status, String reason);

    Flux<ApprovalRequest> queryPending(String sessionId);
}
