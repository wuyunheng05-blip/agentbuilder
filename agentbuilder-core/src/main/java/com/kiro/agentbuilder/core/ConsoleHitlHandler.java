package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;
import java.util.Scanner;

public class ConsoleHitlHandler implements HitlHandler {

    @Override
    public Mono<ApprovalDecision> requestApproval(Tool tool, ToolCall call, ExecutionContext context) {
        return Mono.fromCallable(() -> {
                    System.out.printf(
                            Locale.ROOT,
                            "Approve tool %s with callId=%s and arguments=%s ? (y/n)%n",
                            tool.getName(),
                            call.callId(),
                            call.arguments());
                    Scanner scanner = new Scanner(System.in);
                    String answer = scanner.nextLine();
                    boolean approved = answer != null && answer.trim().toLowerCase(Locale.ROOT).startsWith("y");
                    return approved
                            ? ApprovalDecision.approved("approved via console", null)
                            : ApprovalDecision.rejected("rejected via console", null);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
