package com.kiro.agentbuilder.api.spi;

import reactor.core.publisher.Mono;

public interface EmbeddingProvider {

    Mono<float[]> embed(String text);
}
