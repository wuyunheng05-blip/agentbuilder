package com.kiro.agentbuilder.core;

import com.kiro.agentbuilder.api.Agent;
import com.kiro.agentbuilder.api.model.AgentConfig;
import com.kiro.agentbuilder.api.model.AgentInput;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.model.event.AgentEvent;
import com.kiro.agentbuilder.api.model.event.AgentEventType;
import com.kiro.agentbuilder.api.model.event.ErrorPayload;
import com.kiro.agentbuilder.api.model.event.FinalPayload;
import com.kiro.agentbuilder.api.react.Phase;
import com.kiro.agentbuilder.api.storage.AgentSnapshot;
import com.kiro.agentbuilder.memory.ContextWindowManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AgentRuntime implements Agent {

    private final AgentConfig config;
    private final AgentStateMachine stateMachine = new AgentStateMachine();
    private final ExecutionContextFactory contextFactory = new ExecutionContextFactory();
    private final InterceptorChain interceptorChain;
    private final LifecycleHookChain hookChain;
    private final InputPreprocessor inputPreprocessor;
    private final GuardChain guardChain;
    private final ToolRegistry toolRegistry;
    private final ToolGateway toolGateway;
    private final PromptComposer promptComposer;
    private final ReActOrchestrator orchestrator;
    private volatile ExecutionContext currentContext;

    public AgentRuntime(AgentConfig config) {
        this.config = config;
        this.interceptorChain = new InterceptorChain(config.interceptors());
        this.hookChain = new LifecycleHookChain(config.hooks());
        this.inputPreprocessor = new InputPreprocessor(
                config.sensitiveDataMasker(),
                config.memoryStore(),
                config.contextProviders());
        this.guardChain = new GuardChain(config.guards());
        this.toolRegistry = new ToolRegistry(config.tools());
        this.toolGateway = new ToolGateway(toolRegistry, interceptorChain, config.authorizationService());
        this.promptComposer = new PromptComposer(config, new ContextWindowManager(10));
        this.orchestrator = new ReActOrchestrator(buildPhases());
    }

    @Override
    public String getId() {
        return config.agentId();
    }

    @Override
    public Flux<AgentEvent> run(AgentInput input) {
        AgentInput effectiveInput = input == null ? new AgentInput("", config.sessionId(), null, Map.of()) : input;
        return Flux.create(sink -> {
            if (!stateMachine.start()) {
                sink.next(errorEvent("AGENT_BUSY", "Agent is already running"));
                sink.complete();
                return;
            }
            ExecutionContext context = contextFactory.create(config, effectiveInput);
            currentContext = context;
            startRun(context, effectiveInput, sink)
                    .doFinally(signalType -> cleanup(context))
                    .subscribe(
                            ignored -> {
                            },
                            error -> handleFailure(context, sink, error));
        });
    }

    @Override
    public Flux<AgentEvent> resume(String snapshotId, AgentInput input) {
        return config.storageModule().snapshotStore().load(snapshotId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Snapshot not found: " + snapshotId)))
                .flatMapMany(snapshot -> Flux.create(sink -> {
                    if (!stateMachine.start()) {
                        sink.next(errorEvent("AGENT_BUSY", "Agent is already running"));
                        sink.complete();
                        return;
                    }
                    ExecutionContext context = contextFactory.create(config, input, snapshot);
                    currentContext = context;
                    startRun(context, input, sink)
                            .doFinally(signalType -> cleanup(context))
                            .subscribe(
                                    ignored -> {
                                    },
                                    error -> handleFailure(context, sink, error));
                }));
    }

    @Override
    public void shutdown() {
        if (currentContext != null) {
            currentContext.cancellationToken().cancel();
        }
    }

    private Mono<Void> startRun(ExecutionContext context, AgentInput input, FluxSink<AgentEvent> sink) {
        hookChain.onStart(context, input);
        return interceptorChain.beforeRun(context, input)
                .flatMap(preparedInput -> inputPreprocessor.process(context, preparedInput))
                .flatMap(preparedInput -> guardChain.check(context)
                        .flatMap(result -> result.allowed()
                                ? Mono.just(preparedInput)
                                : Mono.error(new IllegalStateException(result.rejectReason()))))
                .doOnNext(preparedInput -> sink.next(AgentEvent.of(AgentEventType.RUN_START, context.runId(), preparedInput)))
                .flatMap(preparedInput -> orchestrator.orchestrate(context, sink))
                .flatMap(payload -> emitFinal(context, sink, payload))
                .timeout(config.runTimeout().plus(Duration.ofSeconds(2)));
    }

    private Mono<Void> emitFinal(ExecutionContext context, FluxSink<AgentEvent> sink, FinalPayload payload) {
        AgentEvent finalEvent = AgentEvent.of(AgentEventType.FINAL, context.runId(), payload);
        return interceptorChain.afterRun(context, finalEvent)
                .doOnNext(event -> {
                    hookChain.onComplete(context, payload);
                    sink.next(event);
                    sink.complete();
                })
                .then();
    }

    private AgentEvent errorEvent(String code, String message) {
        return AgentEvent.of(AgentEventType.ERROR, currentContext == null ? getId() : currentContext.runId(), new ErrorPayload(code, message));
    }

    private void handleFailure(ExecutionContext context, FluxSink<AgentEvent> sink, Throwable error) {
        hookChain.onError(context, error);
        sink.next(AgentEvent.of(
                AgentEventType.ERROR,
                context.runId(),
                new ErrorPayload(error.getClass().getSimpleName(), error.getMessage())));
        sink.complete();
    }

    private void cleanup(ExecutionContext context) {
        inputPreprocessor.extract(context).block();
        hookChain.onCleanup(context);
        stateMachine.complete();
        currentContext = null;
    }

    private List<Phase> buildPhases() {
        PhaseTraceRecorder traceRecorder = new PhaseTraceRecorder(hookChain);
        SelfConsistencyValidator validator = new SelfConsistencyValidator(
                config.modelProvider(),
                interceptorChain,
                3,
                0.7d);
        return List.of(
                new PreCheckPhase(config.maxIterations(), 0),
                new ThinkPhase(
                        promptComposer,
                        interceptorChain,
                        config.modelProvider(),
                        new LlmDecisionParser(),
                        toolRegistry.getDefinitions(),
                        traceRecorder),
                new ActionPhase(toolGateway, traceRecorder),
                new TerminationPhase(validator),
                new ReflectionPhase(traceRecorder));
    }
}
