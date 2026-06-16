package com.kiro.agentbuilder.api.extension;

import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.StepTrace;

public interface LifecycleHook {

    default void onStart(ExecutionContext context, AgentInput input) {
    }

    default void onStep(ExecutionContext context, StepTrace stepTrace) {
    }

    default void onComplete(ExecutionContext context, FinalPayload payload) {
    }

    default void onError(ExecutionContext context, Throwable error) {
    }

    default void onCleanup(ExecutionContext context) {
    }
}
