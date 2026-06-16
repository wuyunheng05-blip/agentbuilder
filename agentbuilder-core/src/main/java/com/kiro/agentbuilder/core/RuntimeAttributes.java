package com.kiro.agentbuilder.core;

public final class RuntimeAttributes {

    public static final String CURRENT_ITERATION = "currentIteration";
    public static final String CURRENT_PHASE_INDEX = "currentPhaseIndex";
    public static final String CURRENT_MODEL_RESPONSE = "currentModelResponse";
    public static final String CURRENT_DECISION = "currentDecision";
    public static final String CURRENT_TOOL_RESULTS = "currentToolResults";
    public static final String TERMINAL_PAYLOAD = "terminalPayload";
    public static final String NEXT_DIRECTIVE = "nextDirective";
    public static final String LAST_MODEL_REQUEST = "lastModelRequest";
    public static final String ITERATION_ATTEMPTS = "iterationAttempts";
    public static final String PENDING_APPROVAL_REQUEST_ID = "pendingApprovalRequestId";

    private RuntimeAttributes() {
    }
}
