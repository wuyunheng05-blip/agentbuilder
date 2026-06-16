package com.kiro.agentbuilder.api.react;

import java.util.Optional;

public record PhaseResult(PhaseAction action, Optional<String> reason) {

    public static PhaseResult continueWith() {
        return new PhaseResult(PhaseAction.CONTINUE, Optional.empty());
    }

    public static PhaseResult terminate(String reason) {
        return new PhaseResult(PhaseAction.TERMINATE, Optional.ofNullable(reason));
    }

    public static PhaseResult asyncBoundary(String reason) {
        return new PhaseResult(PhaseAction.ASYNC_BOUNDARY, Optional.ofNullable(reason));
    }

    public static PhaseResult retryIteration(String reason) {
        return new PhaseResult(PhaseAction.RETRY_ITERATION, Optional.ofNullable(reason));
    }

    public static PhaseResult nextIteration(String reason) {
        return new PhaseResult(PhaseAction.NEXT_ITERATION, Optional.ofNullable(reason));
    }
}
