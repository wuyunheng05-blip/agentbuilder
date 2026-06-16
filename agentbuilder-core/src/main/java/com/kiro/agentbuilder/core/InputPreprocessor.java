package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.extension.ContextProvider;
import com.kiro.agentbuilder.api.memory.MemoryQuery;
import com.kiro.agentbuilder.api.memory.MemoryStore;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.Message;
import com.kiro.agentbuilder.api.spi.SensitiveDataMasker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class InputPreprocessor {

    private final SensitiveDataMasker masker;
    private final MemoryStore memoryStore;
    private final List<ContextProvider> contextProviders;

    public InputPreprocessor(SensitiveDataMasker masker, MemoryStore memoryStore, List<ContextProvider> contextProviders) {
        this.masker = masker;
        this.memoryStore = memoryStore;
        this.contextProviders = List.copyOf(contextProviders);
    }

    public Mono<AgentInput> process(ExecutionContext context, AgentInput input) {
        AgentInput maskedInput = new AgentInput(
                masker.mask(input.content()),
                input.sessionId(),
                input.userId(),
                input.metadata());
        context.attributes().put("input", maskedInput);
        if (!context.messageHistory().isEmpty()) {
            context.attributes().put("maskedInput", maskedInput.content());
        }
        return injectMemory(context, maskedInput)
                .then(Flux.fromIterable(contextProviders).concatMap(provider -> provider.inject(context)).then())
                .thenReturn(maskedInput);
    }

    public Mono<Void> extract(ExecutionContext context) {
        return Flux.fromIterable(contextProviders).concatMap(provider -> provider.extract(context)).then();
    }

    private Mono<Void> injectMemory(ExecutionContext context, AgentInput input) {
        return memoryStore.query(new MemoryQuery(context.agentId(), null, input.content(), null, 3, 0))
                .map(entry -> "- " + entry.content())
                .collectList()
                .doOnNext(memories -> {
                    if (!memories.isEmpty()) {
                        context.addMessage(new Message(
                                com.kiro.agentbuilder.api.model.MessageRole.SYSTEM,
                                "Relevant memory:\n" + String.join("\n", memories),
                                "memory",
                                null,
                                Map.of()));
                    }
                })
                .then();
    }
}
