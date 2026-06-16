package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.model.AgentStatus;

import java.util.concurrent.atomic.AtomicReference;

public class AgentStateMachine {

    private final AtomicReference<AgentStatus> state = new AtomicReference<>(AgentStatus.IDLE);

    public boolean tryTransition(AgentStatus from, AgentStatus to) {
        return state.compareAndSet(from, to);
    }

    public AgentStatus current() {
        return state.get();
    }

    public boolean start() {
        return tryTransition(AgentStatus.IDLE, AgentStatus.RUNNING);
    }

    public void complete() {
        state.set(AgentStatus.IDLE);
    }

    public void fail() {
        state.set(AgentStatus.ERROR);
    }
}
