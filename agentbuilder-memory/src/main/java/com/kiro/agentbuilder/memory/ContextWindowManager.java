package com.kiro.agentbuilder.memory;

import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.model.MessageRole;

import java.util.ArrayList;
import java.util.List;

public class ContextWindowManager {

    private final int windowSize;

    public ContextWindowManager(int windowSize) {
        this.windowSize = Math.max(1, windowSize);
    }

    public List<Message> trim(List<Message> history) {
        List<Message> systemMessages = history.stream()
                .filter(message -> message.role() == MessageRole.SYSTEM)
                .toList();
        List<Message> others = history.stream()
                .filter(message -> message.role() != MessageRole.SYSTEM)
                .toList();
        int fromIndex = Math.max(0, others.size() - windowSize);
        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(others.subList(fromIndex, others.size()));
        return result;
    }
}
