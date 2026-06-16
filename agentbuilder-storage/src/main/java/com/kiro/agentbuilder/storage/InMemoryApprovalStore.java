package com.kiro.agentbuilder.storage;

import com.kiro.agentbuilder.api.storage.ApprovalRequest;
import com.kiro.agentbuilder.api.storage.ApprovalStatus;
import com.kiro.agentbuilder.api.storage.ApprovalStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryApprovalStore implements ApprovalStore {

    private final Map<String, ApprovalRequest> requests = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(ApprovalRequest request) {
        requests.put(request.approvalRequestId(), request);
        return Mono.empty();
    }

    @Override
    public Mono<ApprovalRequest> get(String approvalRequestId) {
        return Mono.justOrEmpty(requests.get(approvalRequestId));
    }

    @Override
    public Mono<ApprovalRequest> resolve(String approvalRequestId, ApprovalStatus status, String reason) {
        ApprovalRequest current = requests.get(approvalRequestId);
        if (current == null) {
            return Mono.empty();
        }
        ApprovalRequest updated = current.withDecision(status, reason);
        requests.put(approvalRequestId, updated);
        return Mono.just(updated);
    }

    @Override
    public Flux<ApprovalRequest> queryPending(String sessionId) {
        return Flux.fromStream(requests.values().stream()
                .filter(request -> request.status() == ApprovalStatus.PENDING)
                .filter(request -> sessionId == null || sessionId.equals(request.sessionId()))
                .sorted(Comparator.comparing(ApprovalRequest::createdAt)));
    }
}
