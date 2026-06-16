package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.api.storage.ApprovalRequest;
import com.kiro.agentbuilder.api.storage.ApprovalStatus;
import com.kiro.agentbuilder.api.storage.ApprovalStore;
import com.kiro.agentbuilder.api.storage.SnapshotStore;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class PausingHitlHandler implements HitlHandler {

    public static final String APPROVAL_REQUEST_ID_KEY = "approvalRequestId";
    public static final String APPROVED_KEY = "approved";
    public static final String REJECTION_REASON_KEY = "rejectionReason";

    private final ApprovalStore approvalStore;
    private final SnapshotStore snapshotStore;
    private final ExecutionSnapshotMapper snapshotMapper;

    public PausingHitlHandler(ApprovalStore approvalStore, SnapshotStore snapshotStore, ExecutionSnapshotMapper snapshotMapper) {
        this.approvalStore = approvalStore;
        this.snapshotStore = snapshotStore;
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public Mono<ApprovalDecision> requestApproval(Tool tool, ToolCall call, ExecutionContext context) {
        AgentInput input = context.attributes().get("input") instanceof AgentInput agentInput ? agentInput : null;
        Map<String, Object> metadata = input == null ? Map.of() : input.metadata();
        String pendingRequestId = stringValue(context.attributes().get(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID));
        String incomingRequestId = stringValue(metadata.get(APPROVAL_REQUEST_ID_KEY));

        if (incomingRequestId != null) {
            return approvalStore.get(incomingRequestId)
                    .flatMap(existing -> resolveExistingApproval(existing, metadata, context))
                    .switchIfEmpty(Mono.error(new IllegalStateException("Approval request not found: " + incomingRequestId)));
        }

        if (pendingRequestId != null) {
            return approvalStore.get(pendingRequestId)
                    .flatMap(existing -> Mono.just(toDecision(existing, context)))
                    .switchIfEmpty(createPendingApproval(tool, call, context, pendingRequestId));
        }

        return createPendingApproval(tool, call, context, UUID.randomUUID().toString());
    }

    private Mono<ApprovalDecision> resolveExistingApproval(
            ApprovalRequest existing,
            Map<String, Object> metadata,
            ExecutionContext context) {
        if (metadata.containsKey(APPROVED_KEY)) {
            boolean approved = Boolean.parseBoolean(String.valueOf(metadata.get(APPROVED_KEY)));
            ApprovalStatus nextStatus = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
            String reason = approved ? "approval granted" : stringValue(metadata.get(REJECTION_REASON_KEY));
            return approvalStore.resolve(existing.approvalRequestId(), nextStatus, reason)
                    .map(updated -> toDecision(updated, context));
        }
        return Mono.just(toDecision(existing, context));
    }

    private Mono<ApprovalDecision> createPendingApproval(
            Tool tool,
            ToolCall call,
            ExecutionContext context,
            String approvalRequestId) {
        context.attributes().put(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID, approvalRequestId);
        context.attributes().put(RuntimeAttributes.CURRENT_PHASE_INDEX, 2);
        AgentSnapshot snapshot = snapshotMapper.toSnapshot(context, "APPROVAL_REQUIRED");
        ApprovalRequest request = new ApprovalRequest(
                approvalRequestId,
                context.sessionId(),
                context.runId(),
                context.agentId(),
                snapshot.snapshotId(),
                call,
                ApprovalStatus.PENDING,
                "Approval required for tool: " + tool.getName(),
                null,
                null);
        return snapshotStore.save(snapshot)
                .then(approvalStore.save(request))
                .thenReturn(request)
                .doOnNext(saved -> context.attributes().put(SnapshotAttributes.LATEST_SNAPSHOT_ID, saved.snapshotId()))
                .map(saved -> ApprovalDecision.pending(saved.reason(), saved.approvalRequestId()));
    }

    private ApprovalDecision toDecision(ApprovalRequest request, ExecutionContext context) {
        context.attributes().put(SnapshotAttributes.LATEST_SNAPSHOT_ID, request.snapshotId());
        if (request.status() == ApprovalStatus.APPROVED) {
            context.attributes().remove(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID);
            return ApprovalDecision.approved(
                    request.reason() == null ? "approval granted" : request.reason(),
                    request.approvalRequestId());
        }
        if (request.status() == ApprovalStatus.REJECTED) {
            context.attributes().remove(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID);
            return ApprovalDecision.rejected(
                    request.reason() == null ? "approval rejected" : request.reason(),
                    request.approvalRequestId());
        }
        context.attributes().put(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID, request.approvalRequestId());
        context.attributes().put(RuntimeAttributes.CURRENT_PHASE_INDEX, 2);
        return ApprovalDecision.pending(
                request.reason() == null ? "Approval required" : request.reason(),
                request.approvalRequestId());
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
