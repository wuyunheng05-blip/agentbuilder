package com.kiro.agentbuilder.api.model;

public record ModelEvent(ModelEventType type, String contentDelta, ModelResponse response, Throwable error) {

    public static ModelEvent delta(String contentDelta) {
        return new ModelEvent(ModelEventType.CONTENT_DELTA, contentDelta, null, null);
    }

    public static ModelEvent complete(ModelResponse response) {
        return new ModelEvent(ModelEventType.COMPLETE, null, response, null);
    }

    public static ModelEvent error(Throwable error) {
        return new ModelEvent(ModelEventType.ERROR, null, null, error);
    }
}
