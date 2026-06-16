package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class PausingHitlHandler implements HitlHandler {

    public static final String APPROVAL_REQUEST_ID_KEY = "approvalRequestId";
    public static final String APPROVED_KEY = "approved";
    public static final String REJECTION_REASON_KEY = "rejectionReason";

    @Override
    public Mono<ApprovalDecision> requestApproval(Tool tool, ToolCall call, ExecutionContext context) {
        AgentInput input = context.attributes().get("input") instanceof AgentInput agentInput ? agentInput : null;
        Map<String, Object> metadata = input == null ? Map.of() : input.metadata();
        String pendingRequestId = stringValue(context.attributes().get(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID));
        String incomingRequestId = stringValue(metadata.get(APPROVAL_REQUEST_ID_KEY));

        if (pendingRequestId != null && pendingRequestId.equals(incomingRequestId)) {
            boolean approved = Boolean.parseBoolean(String.valueOf(metadata.getOrDefault(APPROVED_KEY, "false")));
            context.attributes().remove(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID);
            if (approved) {
                return Mono.just(ApprovalDecision.approved("approval granted", pendingRequestId));
            }
            String reason = stringValue(metadata.get(REJECTION_REASON_KEY));
            return Mono.just(ApprovalDecision.rejected(
                    reason == null ? "approval rejected" : reason,
                    pendingRequestId));
        }

        String approvalRequestId = pendingRequestId != null ? pendingRequestId : UUID.randomUUID().toString();
        context.attributes().put(RuntimeAttributes.PENDING_APPROVAL_REQUEST_ID, approvalRequestId);
        context.attributes().put(RuntimeAttributes.CURRENT_PHASE_INDEX, 2);
        return Mono.just(ApprovalDecision.pending(
                "Approval required for tool: " + tool.getName(),
                approvalRequestId));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
