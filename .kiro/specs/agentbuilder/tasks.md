# AgentBuilder — 实现任务列表

> 参考文档：#[[file:requirements.md]] · #[[file:design.md]]
>
> 技术栈：Java 8 · Project Reactor · OkHttp · Jackson · Redis/Jedis · OpenTelemetry SDK · SLF4J

---

## Phase 1：项目骨架与核心 API 定义

### Task 1.1 — 初始化 Maven 多模块项目结构

**目标**：搭建完整的多模块 Maven 父子项目，定义所有模块依赖关系和版本管理。

实现步骤：
1. 创建根 `pom.xml`，定义 `<modules>` 列表：`agentbuilder-api`、`agentbuilder-core`、`agentbuilder-memory`、`agentbuilder-tools`、`agentbuilder-multi-agent`、`agentbuilder-governance`、`agentbuilder-observability`、`agentbuilder-storage`、`agentbuilder-evolution`、`agentbuilder-test`
2. 在 `<dependencyManagement>` 中统一声明所有依赖版本：
   - `io.projectreactor:reactor-core:3.6.x`
   - `com.squareup.okhttp3:okhttp:4.12.x`
   - `com.fasterxml.jackson.core:jackson-databind:2.17.x`
   - `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`
   - `redis.clients:jedis:5.x`
   - `io.opentelemetry:opentelemetry-api:1.38.x`
   - `io.opentelemetry:opentelemetry-sdk`
   - `io.opentelemetry:opentelemetry-exporter-otlp`
   - `org.slf4j:slf4j-api:2.0.x`
   - `ch.qos.logback:logback-classic:1.5.x`
   - `org.junit.jupiter:junit-jupiter:5.10.x`
   - `org.mockito:mockito-core:5.x`
   - `net.jqwik:jqwik:1.8.x`
3. 设置 Java 8 编译级别：`maven-compiler-plugin` source/target = `1.8`
4. 创建各子模块的 `pom.xml`，声明模块间依赖

**验收**：`mvn clean compile` 全部通过，无编译错误

---

### Task 1.2 — 定义核心 API 接口（agentbuilder-api 模块）

**目标**：定义所有对外公开的接口、枚举、数据模型。这些接口构成框架的合约，不含实现代码。

实现步骤：
1. **智能体接口**：
   - `Agent` 接口：`getId()`、`run(AgentInput): Flux<AgentEvent>`、`resume(String snapshotId, AgentInput): Flux<AgentEvent>`、`shutdown()`
   - `AgentBuilder` 接口（建造者）：`systemPrompt()`、`tool()`、`memory()`、`interceptor()`、`hook()`、`model()`、`maxIterations()`、`sessionId()`、`build(): Agent`
2. **输入/输出模型**：
   - `AgentInput`：`content`、`sessionId`、`userId`、`metadata`（`Map<String,Object>`）
   - `AgentEvent`：`type`（`AgentEventType`）、`runId`、`timestamp`、`payload`
   - `AgentEventType` 枚举：`RUN_START / THINKING_DELTA / CONTENT_DELTA / TOOL_CALL / TOOL_RESULT / MEMORY_RETRIEVED / AGGREGATE_RESULT / FINAL / ERROR`
3. **推理模型**：
   - `PhaseAction` 枚举：`CONTINUE / TERMINATE / ASYNC_BOUNDARY / RETRY_ITERATION / NEXT_ITERATION`
   - `PhaseResult`：`action`、`reason`（Optional）及静态工厂方法
   - `Phase` 接口：`name()`、`execute(ExecutionContext, FluxSink<AgentEvent>): PhaseResult`
4. **工具模型**：
   - `Tool` 接口：`getName()`、`getDescription()`、`getParameterSchema()`、`getRiskLevel()`、`isIdempotent()`、`execute(ToolCall, ExecutionContext): Mono<ToolResult>`
   - `ToolCall`：`toolName`、`arguments`（`Map<String,Object>`）、`callId`
   - `ToolResult`：`callId`、`output`、`isError`、`metadata`
   - `RiskLevel` 枚举：`LOW / MEDIUM / HIGH / CRITICAL`
5. **记忆模型**：
   - `MemoryType` 枚举：`WORKING / EPISODIC / SEMANTIC`
   - `MemoryEntry`：全字段（含 `embedding float[]`、`importanceScore`）
   - `MemoryQuery`：查询条件（含 `queryEmbedding`、`topK`、`minImportance`）
   - `MemoryStore` 接口：`save()`、`query(): Flux<MemoryEntry>`、`delete()`
6. **事件 payload 模型**：
   - `FinalPayload`、`ToolCallPayload`、`ToolResultPayload`、`ThinkingDeltaPayload`
7. **状态机**：
   - `AgentStatus` 枚举：`IDLE / RUNNING / ERROR / CANCELLED / TIMEOUT`
   - `TokenUsage`：`inputTokens`、`outputTokens`、`totalTokens`

**验收**：所有接口可编译，`AgentEvent` 可承载各类 payload，无循环依赖

---

### Task 1.3 — 定义 SPI 扩展点接口

**目标**：定义所有 Java SPI 接口及 `META-INF/services` 声明。

实现步骤：
1. 在 `agentbuilder-api` 中定义 SPI 接口：
   - `Configuration`：全局配置接口，包含 `createModelProvider()`、`createStorageModule()`、`createMemoryModule()` 等工厂方法
   - `ModelProvider`：`Flux<ModelEvent> call(ModelRequest)`、`String getModelName()`
   - `ToolProvider`：`List<Tool> getTools()`（供自定义工具注册）
   - `EmbeddingProvider`：`Mono<float[]> embed(String text)`
   - `QuotaService`：`Mono<Boolean> checkAndConsume(String agentId, QuotaType type, long amount)`
   - `AuthenticationService`：`Mono<Principal> authenticate(AuthCredentials credentials)`
   - `AuthorizationService`：`Mono<Boolean> authorize(Principal principal, String resource, String action)`
   - `SensitiveDataMasker`：`String mask(String input)`
2. 在 `agentbuilder-core` 中为每个 SPI 接口创建默认实现（Noop 或内存实现）
3. 在 `agentbuilder-core/src/main/resources/META-INF/services/` 下注册默认实现

**验收**：`ServiceLoader.load(Configuration.class)` 可加载到默认实现

---

## Phase 2：运行时核心

### Task 2.1 — 实现 ExecutionContext 与状态机

**目标**：实现执行上下文和智能体状态机。

实现步骤：
1. 实现 `ExecutionContext`：
   - 构造时做消息历史深拷贝，防止并发修改
   - 包含 `TokenBudget`（可消耗令牌总量、已消耗量、剩余量）
   - `recursionDepth`：int，通过 `ExecutionContext.nested()` 方法创建子上下文（depth+1）
   - `CancellationToken`：基于 `AtomicBoolean` 实现，支持取消传播
   - `runId`：UUID 生成
2. 实现 `AgentStateMachine`：
   - 状态：`IDLE → RUNNING（run() 时）→ IDLE（完成后）/ ERROR（出错后）`
   - 使用 `AtomicReference<AgentStatus>` 保证线程安全的状态转换
   - 提供 `tryTransition(from, to)` CAS 方法
3. 实现 `ExecutionContextFactory`：从 `AgentInput` + `AgentConfig` 创建完整 `ExecutionContext`

**验收**：并发调用 `run()` 时状态机正确保护，第二次调用在第一次完成前返回错误

---

### Task 2.2 — 实现拦截器链（InterceptorChain）

**目标**：实现覆盖四个 I/O 边界的拦截器机制，支持短路。

实现步骤：
1. 定义 `AgentInterceptor` 接口，包含四个方法：
   - `Mono<Void> beforeRun(ExecutionContext ctx, AgentInput input)`
   - `Mono<AgentInput> afterRun(ExecutionContext ctx, AgentEvent finalEvent)`（可修改）
   - `Mono<ModelRequest> beforeLlmCall(ExecutionContext ctx, ModelRequest request)`（可修改）
   - `Mono<ModelResponse> afterLlmCall(ExecutionContext ctx, ModelResponse response)`（可修改）
   - `Mono<ToolCall> beforeToolCall(ExecutionContext ctx, ToolCall call)`（可修改，返回 empty 表示短路）
   - `Mono<ToolResult> afterToolCall(ExecutionContext ctx, ToolResult result)`（可修改）
2. 实现 `InterceptorChain`：有序链，用 `Flux.fromIterable(interceptors).concatMap()` 依次调用
3. 短路语义：`beforeToolCall` 返回 `Mono.empty()` 时终止工具调用，返回缓存结果

**验收**：拦截器可修改输入/输出，短路后不执行后续拦截器和工具

---

### Task 2.3 — 实现生命周期钩子（LifecycleHook）

**目标**：实现只读生命周期观察者，内置快照钩子、可观测钩子、记忆整合钩子。

实现步骤：
1. 定义 `LifecycleHook` 接口：
   - `void onStart(ExecutionContext ctx, AgentInput input)`
   - `void onStep(ExecutionContext ctx, StepTrace step)`
   - `void onComplete(ExecutionContext ctx, FinalPayload result)`
   - `void onError(ExecutionContext ctx, Throwable error)`
   - `void onCleanup(ExecutionContext ctx)`
   - 所有方法默认 noop，子类按需覆盖
2. 实现 `SnapshotHook`：`onStep` 时定期（可配置间隔）触发快照保存到 `SnapshotStore`
3. 实现 `ObservabilityHook`：`onStart/onStep/onComplete/onError` 时写入 `ObservabilityStore` 和 OpenTelemetry Span
4. 实现 `MemoryConsolidationHook`：`onComplete` 时异步将高重要性工作记忆转移到情节/语义层
5. `LifecycleHookChain`：遍历所有 hook 顺序调用，catch 异常不影响主流程

**验收**：hook 抛出异常时主流程不中断；快照在指定步数后被写入存储

---

### Task 2.4 — 实现输入预处理管道

**目标**：在进入推理引擎前完成脱敏、记忆注入、上下文提供者链。

实现步骤：
1. 定义 `ContextProvider` 接口：
   - `Mono<Void> inject(ExecutionContext ctx)`：调用前注入上下文
   - `Mono<Void> extract(ExecutionContext ctx)`：调用后提取并保存状态
2. 实现 `InputPreprocessor`，按序执行：
   1. `SensitiveDataMasker.mask(input.getContent())` — 脱敏
   2. `MemoryContextProvider.inject()` — 将相关记忆检索结果注入上下文
   3. 遍历 `List<ContextProvider>`，逐一调用 `inject()`
3. 实现 `RegexSensitiveDataMasker`（默认实现）：
   - 正则匹配：手机号（`1[3-9]\d{9}`）、邮箱、18位身份证、银行卡（16-19位数字）、API Key（`[A-Za-z0-9]{32,}`）
   - 替换为 `[MASKED]`

**验收**：包含手机号的输入经过处理后手机号被替换为 `[MASKED]`

---

### Task 2.5 — 实现守卫链（GuardChain）

**目标**：在执行前快速拒绝不合法请求。

实现步骤：
1. 定义 `ExecutionGuard` 接口：`Mono<GuardResult> check(ExecutionContext ctx)`
2. `GuardResult`：`allowed`（boolean）、`rejectReason`（String）
3. 实现内置守卫：
   - `RecursionDepthGuard`：`ctx.recursionDepth > config.maxRecursionDepth(default=2)` 时拒绝
   - `QuotaGuard`：调用 `QuotaService.checkAndConsume()` 检查配额
   - `CancellationGuard`：检查 `CancellationToken.isCancelled()`
4. `GuardChain.check()`：串行检查所有 guard，遇到第一个拒绝即返回 `AgentEvent.ERROR`

**验收**：递归深度超过 2 时立即返回错误事件，不进入推理引擎

---

## Phase 3：推理引擎

### Task 3.1 — 实现 ReAct 阶段管线编排器

**目标**：实现 `ReActOrchestrator`，驱动 Phase 链按控制流模型执行。

实现步骤：
1. 实现 `ReActOrchestrator.orchestrate(ExecutionContext ctx): Flux<AgentEvent>`：
   ```java
   return Flux.create(sink -> {
       int iteration = 0;
       while (iteration <= maxIterations && !ctx.isCancelled()) {
           boolean exitLoop = false;
           for (Phase phase : phases) {
               PhaseResult result = phase.execute(ctx, sink);
               switch (result.getAction()) {
                   case CONTINUE: break;
                   case TERMINATE: sink.complete(); return;
                   case ASYNC_BOUNDARY: return; // phase 自己管理回调
                   case RETRY_ITERATION: // break inner, continue outer
                       exitLoop = true; break;
                   case NEXT_ITERATION:
                       exitLoop = true; break;
               }
               if (exitLoop) break;
           }
           iteration++;
       }
       // 达到最大迭代数
       sink.error(new MaxIterationsExceededException(maxIterations));
   });
   ```
2. 注册标准 Phase 列表（按顺序）：`PreCheckPhase` → `ThinkPhase` → `ActionPhase` → `TerminationPhase` → `ReflectionPhase`
3. 支持通过构造器注入自定义 Phase 列表（供测试和扩展）
4. 发布 `THINKING_DELTA` 和其他流式事件到 `FluxSink`

**验收**：Phase 返回 `TERMINATE` 时循环立即退出；达到 `maxIterations` 时抛出异常

---

### Task 3.2 — 实现 PreCheckPhase

**目标**：每轮迭代开始前检查资源预算和超时。

实现步骤：
1. 实现 `PreCheckPhase`：
   - 检查 `TokenBudget.remaining() > minReservedTokens`，否则 TERMINATE
   - 检查 wall-clock 超时（`ctx.getStartTime() + timeout < now()`），否则 TERMINATE，发出 `AgentEvent.ERROR`（TIMEOUT）
   - 检查迭代数上限（作为二次保护）
   - 检查 `CancellationToken`
2. 以上所有检查通过 → 返回 `PhaseResult.continueWith()`

**验收**：Token 耗尽时推理循环优雅终止，不抛异常

---

### Task 3.3 — 实现 ThinkPhase（思考阶段）

**目标**：构造 Prompt，调用 LLM，解析决策结果。

实现步骤：
1. 实现 `PromptBuilder`：
   - 组装 SystemPrompt + 消息历史（经滑动窗口截取）+ 可用工具列表描述 + 当前迭代上下文
2. 实现 `ThinkPhase.execute()`：
   - 调用 `promptBuilder.build(ctx)` 生成 `ModelRequest`
   - 通过 `InterceptorChain.beforeLlmCall()` 处理请求
   - 调用 `ModelProvider.call(request)` 获得 `Flux<ModelEvent>`
   - 将 LLM 流式内容发布为 `THINKING_DELTA` 事件到 sink
   - 收集完整响应后调用 `InterceptorChain.afterLlmCall()`
   - 调用 `LlmDecisionParser.parse(response)` 解析为：`ToolCallDecision | FinalAnswerDecision | NoActionDecision`
   - 将解析结果存入 `ctx.attributes["currentDecision"]`
3. 由于 LLM 调用是异步的，返回 `PhaseResult.asyncBoundary()`，在 LLM 回调中恢复管线

**验收**：ThinkPhase 可解析标准 tool_call 格式和 final_answer 格式的 LLM 响应

---

### Task 3.4 — 实现 ActionPhase（行动阶段）

**目标**：根据 ThinkPhase 的决策通过工具网关执行工具调用。

实现步骤：
1. 实现 `ActionPhase.execute()`：
   - 从 `ctx.attributes["currentDecision"]` 获取决策
   - 若为 `NoActionDecision`：直接返回 `CONTINUE`（TerminationPhase 处理修正逻辑）
   - 若为 `FinalAnswerDecision`：直接返回 `CONTINUE`（TerminationPhase 处理终止）
   - 若为 `ToolCallDecision`：
     - 发布 `TOOL_CALL` 事件到 sink
     - 调用 `ToolGateway.execute(toolCall, ctx)` → `Mono<ToolResult>`
     - 发布 `TOOL_RESULT` 事件到 sink
     - 将工具结果追加到 `ctx.messageHistory` 作为 tool_result 消息
   - 异步执行，返回 `PhaseResult.asyncBoundary()`，在工具回调中恢复管线

**验收**：工具执行结果正确追加到消息历史并作为 TOOL_RESULT 事件发出

---

### Task 3.5 — 实现 TerminationPhase 与 ReflectionPhase

**目标**：实现终止决策和步骤反思。

实现步骤：
1. 实现 `TerminationPhase`：
   - 读取 `ctx.attributes["currentDecision"]`
   - `FinalAnswerDecision`：
     - 若置信度低于阈值（可配置），触发 Self-Consistency（多次采样，见 Task 3.6）
     - 否则发布 `FINAL` 事件，返回 `TERMINATE`
   - `ToolCallDecision`：工具已执行，返回 `NEXT_ITERATION`
   - `NoActionDecision`：向消息历史注入修正提示（"请明确调用工具或给出最终答案"），返回 `RETRY_ITERATION`
2. 实现 `ReflectionPhase`（非终止情况下执行）：
   - 评估当前迭代步骤质量（通过 LLM 自评或规则评分）
   - 将评估结果记录到 `StepTrace` 并通知 `ObservabilityHook`
   - 返回 `CONTINUE`

**验收**：无动作决策时消息历史中追加修正提示；最终答案时 FINAL 事件携带正确的 Token 统计

---

### Task 3.6 — 实现自一致性验证（Self-Consistency）

**目标**：对低置信度答案进行多次 LLM 采样，多数投票确认。

实现步骤：
1. 实现 `SelfConsistencyValidator`：
   - 参数：`samplingCount`（默认 3）、`confidenceThreshold`（默认 0.7）
   - 对相同 Prompt 发起 N 次 LLM 调用（`Flux.merge`/并行）
   - 收集 N 个答案，提取语义核心（关键词/摘要）
   - 多数投票：超过半数一致的答案作为最终结果
   - 计算聚合置信度并追加到 `FinalPayload.confidenceScore`
2. 发布 `AGGREGATE_RESULT` 事件，携带所有候选答案和投票结果

**验收**：3 次采样中 2 次答案相似时，最终输出该答案；3 次完全不同时降低置信度标记

---

## Phase 4：工具系统

### Task 4.1 — 实现工具注册表（ToolRegistry）

**目标**：统一管理所有工具的注册、查询、SPI 加载。

实现步骤：
1. 实现 `ToolRegistry`：
   - `register(Tool tool)`：注册单个工具
   - `registerProvider(ToolProvider provider)`：批量注册
   - `Optional<Tool> find(String toolName)`
   - `List<ToolDefinition> getDefinitions()`：获取供 Prompt 使用的工具描述列表
2. 启动时通过 `ServiceLoader<ToolProvider>` 自动加载 SPI 注册的工具
3. `ToolDefinition`：名称、描述、参数 JSON Schema（供 LLM 决策使用）

**验收**：通过 SPI 文件注册的 `ToolProvider` 可在 registry 中被发现

---

### Task 4.2 — 实现工具网关（ToolGateway）

**目标**：统一工具调用入口，执行完整的网关链路。

实现步骤：
1. 实现 `ToolGateway.execute(ToolCall call, ExecutionContext ctx): Mono<ToolResult>`，按序执行：
   1. `AuthorizationService.authorize(principal, call.getToolName(), "execute")`
   2. `PolicyEngine.validate(call, ctx)`：策略校验（风险等级、预算）
   3. `InterceptorChain.beforeToolCall(ctx, call)` — 支持短路（返回 empty 使用缓存）
   4. 参数 JSON Schema 校验
   5. `RetryPolicy.executeWithRetry(() -> tool.execute(call, ctx))`
   6. `InterceptorChain.afterToolCall(ctx, result)`
   7. `AuditLogger.log(call, result, ctx)` — 审计记录
2. 幂等工具结果缓存：`isIdempotent()` 为 true 时先查缓存，命中则短路
3. 工具不存在时返回 `ToolResult.error("Tool not found: " + toolName)`

**验收**：高风险工具（`RiskLevel.CRITICAL`）触发 HITL 暂停；幂等工具相同参数第二次命中缓存

---

### Task 4.3 — 实现策略引擎（PolicyEngine）

**目标**：工具调用前的策略校验：权限、风险、预算。

实现步骤：
1. 实现 `PolicyEngine.validate(ToolCall call, ExecutionContext ctx): Mono<PolicyResult>`：
   - `RiskLevel.HIGH / CRITICAL` → 触发 `HitlHandler.requestApproval(call, ctx)`
   - Token 预算检查：工具调用预计消耗是否超过剩余预算
   - 时间预算检查：是否超过工具类型允许的最大执行时间
2. 实现 `HitlHandler`（Human-in-the-Loop）：
   - 暂停当前执行流，发布 `TOOL_CALL`（pending approval）事件
   - 等待外部 `ApprovalService.waitForApproval(callId)` → `Mono<Boolean>`
   - 批准则继续，拒绝则返回错误 `ToolResult`
   - 控制台实现：`ConsoleHitlHandler`（打印等待提示，读取用户输入）

**验收**：标记为 CRITICAL 的工具调用暂停并等待审批确认

---

### Task 4.4 — 实现内置工具

**目标**：实现文档中列出的所有内置工具。

实现步骤：
1. **HttpTool**：基于 OkHttp，支持 GET/POST，超时配置，返回 HTTP 响应体
2. **FileReadTool / FileWriteTool**：基于 Java NIO，操作限制在沙箱目录内
3. **KnowledgeRetrievalTool**：
   - 单知识库：直接调用 `EmbeddingProvider` 向量化 + `MemoryStore` 语义搜索
   - 多知识库：`KnowledgeRouter` 按 topic 路由到对应库，聚合结果
4. **CodeSandboxTool**：
   - Java 侧：在隔离目录写入代码文件，通过 `ProcessBuilder` 调用 bwrap 执行
   - 收集 stdout/stderr 作为工具结果
   - 产物检测：执行后扫描输出目录，将新文件作为 artifact 上报
5. **McpClientTool**：
   - 通过 `ProcessBuilder` 启动 MCP Server 进程（Stdio 模式）
   - 实现 MCP JSON-RPC 协议的 `tools/list` 和 `tools/call`
   - 动态注册发现的工具到 `ToolRegistry`

**验收**：HttpTool 可调用公网 URL 并返回响应；KnowledgeRetrievalTool 可从语义记忆检索相关条目

---

## Phase 5：记忆系统

### Task 5.1 — 实现三层记忆存储

**目标**：实现 `MemoryStore` 的内存版和分层管理。

实现步骤：
1. 实现 `InMemoryMemoryStore`：
   - `ConcurrentHashMap<String, MemoryEntry>` 作为存储
   - `save()`：put 操作
   - `query(MemoryQuery)`：支持按 type 过滤、按 importanceScore 排序、topK 截取
   - 文本检索：简单 contains 匹配（内存版）
2. 实现 `LayeredMemoryStore`（分层管理器）：
   - 工作记忆：最近 N 条消息，每次 run 结束可配置保留策略
   - 情节记忆：按 `createdAt` 倒序 + `importanceScore` 加权检索
   - 语义记忆：按 `embedding` 余弦相似度排序（内存版使用朴素向量计算）
3. 实现 `BM25MemoryRetriever`：基于 BM25 算法对 `content` 字段进行相关性排序

**验收**：工作记忆查询返回最近 10 条；语义记忆余弦相似度最高的条目排在前面

---

### Task 5.2 — 实现上下文窗口管理与自动压缩

**目标**：保证注入 LLM 的消息历史不超出 Token 上限。

实现步骤：
1. 实现 `ContextWindowManager`：
   - 滑动窗口：保留最近 `windowSize`（默认 10）轮对话
   - 超出时删除最老轮次（保留 SystemPrompt + 最近 N 轮）
2. 实现 `AutoCompressionGuard`（Phase 前置检查）：
   - 估算当前消息历史 Token 数（`content.length() / 4` 作为近似）
   - 超过 `compressionThreshold`（默认 0.8 × maxContextTokens）时触发压缩
   - 压缩策略：调用 LLM 对最老 K 条消息做摘要，替换为单条摘要消息
3. 实现 `TokenBudgetManager`：
   - 追踪每次 LLM 调用实际消耗（从 ModelResponse 中提取 usage）
   - `remaining()` = `totalBudget - consumed`

**验收**：消息历史超过 10 轮后，旧轮次被截断；Token 使用量被正确累加

---

### Task 5.3 — 实现记忆整合器（MemoryConsolidator）

**目标**：异步将重要工作记忆转移到长期记忆层。

实现步骤：
1. 实现 `MemoryConsolidator`，作为 `LifecycleHook.onComplete()` 的实现：
   - 查询本次 run 的工作记忆，过滤 `importanceScore >= consolidationThreshold`（默认 0.7）
   - 将满足条件的条目复制到情节记忆（`MemoryType.EPISODIC`）
   - 如果有 `EmbeddingProvider`，同时生成 embedding 并存入语义记忆
   - 异步执行（`Schedulers.boundedElastic()`），不阻塞主流程
2. 重要性评分规则（默认实现）：
   - 工具成功结果：0.8
   - 最终答案：0.9
   - 普通消息：0.5

**验收**：run 完成后，重要性评分 ≥ 0.7 的工作记忆异步出现在情节记忆层

---

## Phase 6：多智能体协作

### Task 6.1 — 实现子智能体工具（SubAgentTool）

**目标**：将子智能体封装为工具，供编排者 LLM 调用。

实现步骤：
1. 实现 `SubAgentTool implements Tool`：
   - 持有 `Agent subAgent` 引用
   - `execute(ToolCall call, ExecutionContext ctx): Mono<ToolResult>`：
     - 创建子 `ExecutionContext`（`ctx.nested()`，depth+1）
     - 将父级 `CancellationToken` 注入子上下文（取消传播）
     - 调用 `subAgent.run(subInput)` → `Flux<AgentEvent>`
     - 子流的 `THINKING_DELTA` / `TOOL_CALL` 等中间事件转发到父级 sink
     - 等待子流 `FINAL` 事件，提取内容作为 `ToolResult.output`
   - 支持 `NEEDS_CLARIFICATION` 协议：子智能体返回澄清问题时，ToolResult 包含标记供父级处理
2. 在 `AgentBuilder.subAgent(Agent sub)` 中注册 SubAgentTool

**验收**：编排者调用子智能体工具时，子智能体的中间事件出现在父级流中

---

### Task 6.2 — 实现远程智能体（RemoteAgentTool）

**目标**：通过 HTTP A2A 协议调用远程智能体服务。

实现步骤：
1. 定义 A2A HTTP 协议约定：
   - `POST /a2a/run`：请求体 `{input, sessionId, metadata}`，响应体 `{runId}`
   - `GET /a2a/stream/{runId}`：SSE 流式返回 `AgentEvent` JSON
2. 实现 `RemoteAgentTool`：
   - 基于 OkHttp，带连接池（`ConnectionPool`）
   - 支持自定义认证 Header（通过 `RemoteAgentConfig` 配置）
   - 调用 `/run` 获取 runId，再订阅 SSE 流
   - 解析 SSE 事件为本地 `AgentEvent` 并转发
   - 超时、重试（最多 3 次）、连接失败处理

**验收**：可模拟远程服务器返回 SSE 事件，事件被正确解析和转发

---

### Task 6.3 — 实现工作流编排（WorkflowEngine）

**目标**：执行用户预定义的 DAG 工作流。

实现步骤：
1. 定义工作流 DSL（YAML/JSON）：
   ```yaml
   workflow:
     nodes:
       - id: step1
         type: agent_call
         agentId: analyst
         input: "{{workflow.input}}"
       - id: step2
         type: agent_call
         agentId: writer
         input: "{{step1.output}}"
         dependsOn: [step1]
       - id: step3
         type: parallel
         branches: [branchA, branchB]
   ```
2. 实现 `WorkflowParser`：解析 YAML 为 `WorkflowDefinition`（节点列表 + 边列表）
3. 实现 `WorkflowEngine.execute(WorkflowDefinition, AgentInput): Flux<AgentEvent>`：
   - 拓扑排序节点，并行执行无依赖节点（`Flux.merge`）
   - 条件分支：计算条件表达式（基于 Nashorn/简单 EL）选择路径
   - 循环节点：支持 `while` 条件
   - HITL 暂停：节点标记 `requiresApproval: true` 时暂停
4. 变量解析：`{{step1.output}}` 替换为前驱节点结果

**验收**：两个串行节点按序执行；两个并行节点同时运行

---

### Task 6.4 — 实现规划执行工具（PlanAndExecuteTool）

**目标**：LLM 自主决定任务分解和 Worker 执行策略。

实现步骤：
1. 实现 `PlanAndExecuteTool`：
   - `execute()` 时调用 LLM 生成任务计划（JSON 格式：`[{task, worker, parallel}]`）
   - 解析计划为 `TaskPlan`
   - 按计划分配 Worker（子智能体）并行或串行执行
   - 收集所有 Worker 结果，调用 LLM 做结果聚合
2. Worker 可以是注册的子智能体或直接工具调用
3. 发布子任务进展事件到父级 sink

**验收**：给定一个多步骤任务，LLM 生成 3 步计划，3 个 Worker 并行执行后聚合结果

---

## Phase 7：治理安全

### Task 7.1 — 实现韧性机制（CircuitBreaker + RateLimiter + RetryPolicy）

**目标**：为模型调用和工具执行提供熔断、限流、重试保护。

实现步骤：
1. 实现 `CircuitBreaker`（基于滑动窗口统计）：
   - 状态：CLOSED → OPEN（连续失败 N 次）→ HALF_OPEN（探测）→ CLOSED
   - `Mono<T> protect(Supplier<Mono<T>> operation)` 工具方法
   - 熔断时抛 `CircuitBreakerOpenException`
2. 实现 `RateLimiter`（令牌桶算法）：
   - 基于 `AtomicLong` 实现令牌桶
   - `boolean tryAcquire()`：非阻塞尝试获取令牌
3. 实现 `RetryPolicy`：
   - 指数退避：1s → 2s → 4s
   - 可配置最大重试次数（默认 3）
   - 只重试特定异常类型（网络超时、临时错误）
4. 在 `ModelProvider` 代理层（`ResilienceModelProxy`）集成三者

**验收**：LLM 连续失败 5 次后熔断器打开，后续调用立即失败；重试 3 次后永久失败

---

### Task 7.2 — 实现认证授权拦截器

**目标**：通过拦截器链自动完成认证和授权。

实现步骤：
1. 实现 `AuthInterceptor implements AgentInterceptor`：
   - `beforeRun()` 中：从 `AgentInput.metadata` 提取凭证 → 调用 `AuthenticationService.authenticate()`
   - 将 `Principal` 存入 `ExecutionContext.attributes["principal"]`
2. 实现 `AuthorizationInterceptor implements AgentInterceptor`：
   - `beforeToolCall()` 中：提取 `principal`，调用 `AuthorizationService.authorize(principal, toolName, "execute")`
   - 未授权时返回 `Mono.empty()`（短路，工具不执行）
3. 默认实现（Noop）：`NoopAuthenticationService` 返回匿名 Principal；`NoopAuthorizationService` 全部放行

**验收**：使用真实授权服务时，未授权工具调用被拦截并记录审计日志

---

## Phase 8：存储层

### Task 8.1 — 实现 StorageModule 及内存/文件后端

**目标**：实现不可变的 `StorageModule` 及开发测试用的内存后端。

实现步骤：
1. 实现 `StorageModule`（不可变，final 字段）：
   - `MemoryStore memoryStore`
   - `SnapshotStore snapshotStore`
   - `ObservabilityStore observabilityStore`
   - `OutboxStore outboxStore`
   - 静态工厂：`StorageModule.inMemory()`、`StorageModule.create(AgentConfig config)`
2. 实现 `SnapshotStore` 接口 + `InMemorySnapshotStore`：
   - `save(AgentSnapshot snapshot): Mono<Void>`
   - `load(String snapshotId): Mono<AgentSnapshot>`
   - `AgentSnapshot`：id、sessionId、triggerType、snapshotData（JSON）、expiresAt
3. 实现 `ObservabilityStore` 接口 + `InMemoryObservabilityStore`：
   - `saveRunTrace(RunTrace trace): Mono<Void>`
   - `saveStepTrace(StepTrace trace): Mono<Void>`
   - `queryRunTraces(String sessionId): Flux<RunTrace>`
4. 实现 `OutboxStore` 接口 + `InMemoryOutboxStore`：
   - `publish(OutboxEvent event): Mono<Void>`
   - `pollPending(int maxBatch): Flux<OutboxEvent>`
   - `markPublished(String eventId): Mono<Void>`
5. 实现文件后端（Jackson 序列化到本地 JSON 文件）

**验收**：`StorageModule.inMemory()` 创建后可正常读写；内存存储不会跨实例共享

---

### Task 8.2 — 实现 Redis 后端

**目标**：基于 Jedis 实现 Redis 存储后端，用于分布式部署场景。

实现步骤：
1. 实现 `RedisMemoryStore`：key 格式 `memory:{agentInstanceId}:{type}:{id}`，使用 Redis Hash
2. 实现 `RedisSnapshotStore`：key 格式 `snapshot:{snapshotId}`，设置 TTL（expiresAt）
3. 实现 `RedisOutboxStore`：使用 Redis List (`LPUSH` / `BRPOP`) 实现可靠队列
4. 连接池：`JedisPool` 配置（maxTotal、maxIdle、minIdle）
5. 序列化：Jackson 将对象序列化为 JSON 字符串

**验收**：（集成测试）向 Redis 写入 `MemoryEntry` 后，按 agentInstanceId 可检索到

---

## Phase 9：可观测性

### Task 9.1 — 实现 OpenTelemetry 集成

**目标**：为每次 run 生成完整 Span 树，采集 Token 用量等指标。

实现步骤：
1. 实现 `OtelObservabilityHook implements LifecycleHook`：
   - `onStart()`：创建根 Span（`agent.run`），存入 `ctx.attributes["otel.span"]`
   - `onStep()`：创建子 Span（`agent.step.{phase}`）记录迭代详情
   - `onComplete()`：记录 Token 用量 Attribute，结束根 Span
   - `onError()`：标记 Span 为 ERROR 状态
2. 实现 `OtelMetrics`（静态工具类）：
   - `Counter agentRunCounter`：总执行次数
   - `Histogram llmLatencyHistogram`：LLM 调用延迟分布
   - `Counter tokenUsageCounter`：按模型统计 Token 消耗
   - `Counter toolCallCounter`：按工具名统计调用次数
3. OTLP 导出配置：通过 `Configuration` SPI 提供 `OtlpGrpcSpanExporter` 配置入口

**验收**：执行一次 run 后，OpenTelemetry SDK 内存 exporter 中存在对应的 Span 和 Metric

---

### Task 9.2 — 实现轨迹存储与流式事件帧

**目标**：完整记录每次运行的轨迹数据，提供只读查询接口。

实现步骤：
1. 实现 `TraceRecordingHook implements LifecycleHook`：
   - `onStart()`：创建 `RunTrace`（status=RUNNING）存入 `ObservabilityStore`
   - `onStep()`：创建 `StepTrace` 存入 `ObservabilityStore`
   - `onComplete()`：更新 `RunTrace`（status=SUCCESS，duration，tokenUsage，stepCount）
   - `onError()`：更新 `RunTrace`（status=FAILED）
2. 确保所有 `AgentEventType` 枚举值都有对应的事件发布位置（Task 3.x 中各 Phase 负责发布对应事件）
3. 实现 `TraceQueryService`：
   - `getRunTrace(String runId): Mono<RunTrace>`
   - `getStepTraces(String runId): Flux<StepTrace>`

**验收**：run 完成后，可通过 runId 查到包含完整步骤的轨迹记录

---

## Phase 10：自进化与端到端集成

### Task 10.1 — 实现自进化系统

**目标**：收集执行经验，识别模式，优化策略。

实现步骤：
1. 实现 `ExperienceCollector implements LifecycleHook`：
   - `onComplete()` 时收集：任务类型、工具调用序列、迭代次数、耗时、Token、最终状态
   - 将经验写入 `OutboxStore` 作为异步处理队列
2. 实现 `ExperienceAnalyzer`（定时任务）：
   - 从 `OutboxStore` 消费经验事件
   - 统计工具调用成功率（按工具名分组）
   - 识别失败模式（连续失败同类工具）
3. 实现 `StrategyOptimizer`：
   - 输出 `StrategyAdjustment`：建议调整 maxIterations、工具优先级权重
4. 实现 `SkillEvolver`：
   - 高频成功的工具调用序列生成 "速查表"（存入语义记忆）
   - 为发现的新能力生成工具定义草稿

**验收**：执行 10 次相同类型任务后，经验分析器能识别出最常用的工具序列

---

### Task 10.2 — 端到端集成测试

**目标**：验证完整链路在各种场景下正确工作。

实现步骤：
1. 使用 Mock `ModelProvider` 模拟 LLM 响应，创建端到端测试
2. 测试场景：
   - **单轮问答**：LLM 直接给出最终答案，验证 `FINAL` 事件
   - **工具调用链**：LLM 调用 2 个工具后给出答案，验证事件序列
   - **断点恢复**：执行到一半保存快照，重新加载后继续完成
   - **多智能体委派**：编排者调用子智能体工具，验证事件转发
   - **HITL 审批**：触发高风险工具，验证暂停和恢复
   - **熔断器触发**：LLM 调用失败 5 次，验证熔断器打开
   - **Token 超限**：Token 耗尽时验证优雅终止
   - **递归深度超限**：嵌套调用超过 2 层时快速失败
3. 使用 jqwik 属性测试：随机生成工具调用序列，验证事件序列单调递增（无乱序）

**验收**：所有端到端测试通过；属性测试验证在 100 次随机输入下无异常

---

## 附录：任务依赖关系

```
Task 1.1 → Task 1.2 → Task 1.3
                ↓
         Task 2.1 (ExecutionContext)
         Task 2.2 (InterceptorChain)   ← Task 1.3 (SPI)
         Task 2.3 (LifecycleHook)
         Task 2.4 (InputPreprocessor)
         Task 2.5 (GuardChain)
                ↓
         Task 3.1 (ReActOrchestrator)
         Task 3.2 (PreCheckPhase)
         Task 3.3 (ThinkPhase)         ← Task 2.2 (InterceptorChain)
         Task 3.4 (ActionPhase)        ← Task 4.2 (ToolGateway)
         Task 3.5 (TerminationPhase)
         Task 3.6 (SelfConsistency)
                ↓
         Task 4.1 (ToolRegistry)       ← Task 1.3 (SPI)
         Task 4.2 (ToolGateway)        ← Task 4.1, Task 4.3, Task 7.2
         Task 4.3 (PolicyEngine)
         Task 4.4 (内置工具)
                ↓
         Task 5.1 (MemoryStore)        ← Task 1.2 (MemoryEntry)
         Task 5.2 (ContextWindow)
         Task 5.3 (Consolidator)       ← Task 5.1
                ↓
         Task 6.1–6.4 (多智能体)       ← Task 2.x, Task 4.x
         Task 7.1–7.2 (治理)
         Task 8.1–8.2 (存储)           ← Task 1.2
         Task 9.1–9.2 (可观测)         ← Task 2.3, Task 8.1
         Task 10.1 (自进化)            ← Task 2.3, Task 8.1
         Task 10.2 (集成测试)          ← 全部上游
```
