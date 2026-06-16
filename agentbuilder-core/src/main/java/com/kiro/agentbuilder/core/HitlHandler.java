package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import reactor.core.publisher.Mono;

public interface HitlHandler {

    Mono<ApprovalDecision> requestApproval(Tool tool, ToolCall call, ExecutionContext context);
}
