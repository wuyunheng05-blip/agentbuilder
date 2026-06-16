package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.LifecycleHook;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.storage.StepTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LifecycleHookChain {

    private static final Logger log = LoggerFactory.getLogger(LifecycleHookChain.class);

    private final List<LifecycleHook> hooks;

    public LifecycleHookChain(List<LifecycleHook> hooks) {
        this.hooks = List.copyOf(hooks);
    }

    public void onStart(ExecutionContext context, AgentInput input) {
        hooks.forEach(hook -> invoke(() -> hook.onStart(context, input)));
    }

    public void onStep(ExecutionContext context, StepTrace stepTrace) {
        hooks.forEach(hook -> invoke(() -> hook.onStep(context, stepTrace)));
    }

    public void onComplete(ExecutionContext context, FinalPayload payload) {
        hooks.forEach(hook -> invoke(() -> hook.onComplete(context, payload)));
    }

    public void onError(ExecutionContext context, Throwable error) {
        hooks.forEach(hook -> invoke(() -> hook.onError(context, error)));
    }

    public void onCleanup(ExecutionContext context) {
        hooks.forEach(hook -> invoke(() -> hook.onCleanup(context)));
    }

    private void invoke(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            log.warn("Lifecycle hook execution failed", exception);
        }
    }
}
