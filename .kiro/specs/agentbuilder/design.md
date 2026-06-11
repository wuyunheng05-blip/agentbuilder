# AgentBuilder — 设计文档

## 1. 系统架构概览

AgentBuilder 是一个分层架构的 Java 框架，主执行链路自上而下共 8 层，另有 4 个横切关注点贯穿全栈。

```
┌─────────────────────────────────────────────────────────────────┐
│                          API 层                                  │
│        AgentBuilder · Flux<AgentEvent> run() · 断点恢复          │
├─────────────────────────────────────────────────────────────────┤
│                       构建与装配层                                │
│           AgentConfig → AgentAssembler → Agent (不可变)          │
├─────────────────────────────────────────────────────────────────┤
│                       运行时编排层                                │
│   ExecutionContext · InterceptorChain · LifecycleHooks · Guard   │
├─────────────────────────────────────────────────────────────────┤
│                        推理引擎层                                 │
│    ReActOrchestrator · PhasePipeline · PhaseResult(控制流)       │
├─────────────────────────────────────────────────────────────────┤
│                         工具系统                                  │
│         ToolGateway · PolicyEngine · ToolRegistry                │
├─────────────────────────────────────────────────────────────────┤
│                         记忆系统                                  │
│      MemoryStore(三层) · Consolidator · ContextWindow            │
├─────────────────────────────────────────────────────────────────┤
│                        模型抽象层                                 │
│        ModelProvider · ModelRouter · ResilienceProxy             │
├─────────────────────────────────────────────────────────────────┤
│                       基础设施层                                  │
│     StorageModule · EventBus · ThreadPools · SpiLoader           │
└─────────────────────────────────────────────────────────────────┘
          ↕              ↕              ↕              ↕
      多智能体         治理安全        可观测性         自进化
```

---

## 2. Maven 模块结构

```
agentbuilder/
├── agentbuilder-api/               # 对外 API 接口、模型类、SPI 定义
├── agentbuilder-core/              # 运行时编排、推理引擎、工具网关
├── agentbuilder-memory/            # 三层记忆系统
├── agentbuilder-tools/             # 内置工具实现（RAG、HITL、MCP、沙箱）
├── agentbuilder-multi-agent/       # 多智能体协作（SubAgent、RemoteAgent、Workflow）
├── agentbuilder-governance/        # 治理安全（认证、配额、脱敏、韧性）
├── agentbuilder-observability/     # OpenTelemetry、事件流、轨迹存储
├── agentbuilder-storage/           # StorageModule 及各后端实现
├── agentbuilder-evolution/         # 自进化系统
└── agentbuilder-test/              # 测试工具和集成测试
```

---

## 3. 核心领域模型

### 3.1 Agent 与 AgentConfig

```java
// API 模块 — 构建入口
public interface AgentBuilder {
    AgentBuilder systemPrompt(String systemPrompt);
    AgentBuilder tool(ToolDefinition tool);
    AgentBuilder memory(MemoryModule memory);
    AgentBuilder interceptor(AgentInterceptor interceptor);
    AgentBuilder hook(LifecycleHook hook);
    AgentBuilder model(ModelProvider provider);
    AgentBuilder maxIterations(int max);
    AgentBuilder sessionId(String sessionId);
    Agent build();
}

// 不可变运行时实例
public interface Agent {
    String getId();
    Flux<AgentEvent> run(AgentInput input);
    Flux<AgentEvent> resume(String snapshotId, AgentInput input);
    void shutdown();
}
```

### 3.2 执行上下文

```java
public class ExecutionContext {
    private final String runId;
    private final String sessionId;
    private final List<Message> messageHistory;      // 消息历史副本
    private final TokenBudget tokenBudget;           // Token 预算
    private final int recursionDepth;                // 递归深度
    private final CancellationToken cancellationToken;
    private final Map<String, Object> attributes;    // 扩展属性
}
```

### 3.3 ReAct Phase 管线

```java
public interface Phase {
    String name();
    PhaseResult execute(ExecutionContext ctx, FluxSink<AgentEvent> sink);
}

public enum PhaseAction {
    CONTINUE,           // 执行下一个 Phase
    TERMINATE,          // 结束整个推理循环
    ASYNC_BOUNDARY,     // 异步执行，Phase 自行管理回调
    RETRY_ITERATION,    // 从头重试本轮
    NEXT_ITERATION      // 进入下一轮迭代
}

public class PhaseResult {
    private final PhaseAction action;
    private final Optional<String> reason;
    public static PhaseResult continueWith() { ... }
    public static PhaseResult terminate(String reason) { ... }
    public static PhaseResult asyncBoundary() { ... }
}
```

### 3.4 工具模型

```java
public interface Tool {
    String getName();
    String getDescription();
    JsonSchema getParameterSchema();
    RiskLevel getRiskLevel();               // LOW / MEDIUM / HIGH / CRITICAL
    boolean isIdempotent();
    Mono<ToolResult> execute(ToolCall call, ExecutionContext ctx);
}

public interface ToolProvider {                // SPI 接口
    List<Tool> getTools();
}

public class ToolCall {
    private final String toolName;
    private final Map<String, Object> arguments;
    private final String callId;
}

public class ToolResult {
    private final String callId;
    private final Object output;
    private final boolean isError;
    private final Map<String, Object> metadata;
}
```

### 3.5 记忆模型

```java
public enum MemoryType { WORKING, EPISODIC, SEMANTIC }

public class MemoryEntry {
    private final String id;
    private final String agentInstanceId;
    private final MemoryType type;
    private final String content;
    private final float[] embedding;            // 语义记忆才有
    private final double importanceScore;       // 0.0 ~ 1.0
    private final Instant createdAt;
    private final Map<String, Object> metadata;
}

public interface MemoryStore {
    Mono<Void> save(MemoryEntry entry);
    Flux<MemoryEntry> query(MemoryQuery query);
    Mono<Void> delete(String entryId);
}

public class MemoryQuery {
    private final String agentInstanceId;
    private final MemoryType type;
    private final String textQuery;            // BM25 文本检索
    private final float[] queryEmbedding;      // 语义相似检索
    private final int topK;
    private final double minImportance;
}
```

### 3.6 流式事件帧

```java
public enum AgentEventType {
    RUN_START, THINKING_DELTA, CONTENT_DELTA,
    TOOL_CALL, TOOL_RESULT, MEMORY_RETRIEVED,
    AGGREGATE_RESULT, FINAL, ERROR
}

public class AgentEvent {
    private final AgentEventType type;
    private final String runId;
    private final Instant timestamp;
    private final Object payload;              // 各事件专用 payload
}

// FINAL 事件 payload
public class FinalPayload {
    private final String content;
    private final AgentStatus status;          // SUCCESS / FAILED / TIMEOUT / CANCELLED
    private final TokenUsage tokenUsage;
    private final double confidenceScore;
    private final int iterationCount;
}
```

### 3.7 存储模块

```java
public class StorageModule {                   // 不可变
    private final MemoryStore memoryStore;
    private final SnapshotStore snapshotStore;
    private final ObservabilityStore observabilityStore;
    private final OutboxStore outboxStore;

    // 通过 SPI 工厂方法创建
    public static StorageModule create(AgentConfig config) { ... }
    public static StorageModule inMemory() { ... }
}
```

---

## 4. 组件交互序列

### 4.1 核心执行序列

```
调用方                   Agent                 ReActOrchestrator         ToolGateway
  │                       │                           │                       │
  │── run(input) ────────>│                           │                       │
  │                       │── validateState()         │                       │
  │                       │── createContext()         │                       │
  │                       │── runGuards()             │                       │
  │                       │── preprocessInput()       │                       │
  │                       │── orchestrate() ─────────>│                       │
  │                       │                           │── PreCheckPhase       │
  │                       │                           │── ThinkPhase          │
  │<── RUN_START ─────────│<── sink.next() ───────────│                       │
  │<── THINKING_DELTA ────│<── sink.next() ───────────│                       │
  │                       │                           │── ActionPhase ───────>│
  │<── TOOL_CALL ─────────│<── sink.next() ───────────│<─────────────────────│
  │<── TOOL_RESULT ───────│<── sink.next() ───────────│                       │
  │                       │                           │── TerminationPhase    │
  │                       │                           │── ReflectionPhase     │
  │<── FINAL ─────────────│<── sink.complete() ───────│                       │
```

### 4.2 多智能体委派序列

```
编排者 Agent              SubAgentTool             子 Agent
     │                        │                       │
     │── toolcall(delegate)──>│                       │
     │                        │── run(task) ─────────>│
     │<── TOOL_CALL event ────│<── sink.forward() ────│
     │<── TOOL_RESULT event ──│<── FINAL event ───────│
```

---

## 5. 关键设计决策

### 5.1 Builder 两阶段模式

**为什么**：避免在 `run()` 时进行组件装配，确保运行时的不可变性和线程安全。

```
AgentBuilder（收集配置）
    │
    ▼
AgentConfig（配置快照，不可变）
    │
    ▼
AgentAssembler（依赖注入、组件装配）
    │
    ▼
AgentRuntime（运行时实例，不可变）
```

### 5.2 Phase Pipeline 控制流

**为什么**：替代大量 if-else，让每个阶段聚焦自身逻辑，控制流语义由 `PhaseResult.action` 表达，易于测试和扩展新 Phase。

### 5.3 Hook vs Interceptor 职责分离

| 维度 | Hook | Interceptor |
|------|------|-------------|
| 角色 | 旁观者（只读） | 参与者（可变换） |
| 能力 | 观察生命周期事件 | 修改/短路 I/O |
| 用途 | 快照、可观测、记忆整合 | 缓存、安全拒绝、限流 |
| 失败影响 | 不影响主流程 | 可终止执行 |

### 5.4 StorageModule 不可变模式

**为什么**：不可变对象天然线程安全，测试时可直接注入 mock，运行时无需加锁。

### 5.5 响应式流背压

所有执行结果以 `Flux<AgentEvent>` 返回，消费者可按需拉取（背压），避免慢消费者导致 OOM。

---

## 6. 数据库表结构

```sql
-- 检查点表
CREATE TABLE agent_checkpoint (
    session_id       VARCHAR(64)   PRIMARY KEY,
    agent_state      VARCHAR(32)   NOT NULL,       -- IDLE/RUNNING/ERROR
    definition_hash  VARCHAR(64)   NOT NULL,
    version          INT           NOT NULL DEFAULT 1,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL
);

-- 记忆条目表
CREATE TABLE memory_entry (
    id               VARCHAR(64)   PRIMARY KEY,
    agent_instance_id VARCHAR(64)  NOT NULL,
    memory_type      VARCHAR(16)   NOT NULL,       -- WORKING/EPISODIC/SEMANTIC
    content          TEXT          NOT NULL,
    embedding        BLOB,                          -- 向量数据（SEMANTIC 使用）
    importance_score DOUBLE        NOT NULL DEFAULT 0.5,
    created_at       TIMESTAMP     NOT NULL,
    metadata         TEXT,                          -- JSON
    INDEX idx_instance_type_importance (agent_instance_id, memory_type, importance_score)
);

-- 事件 Outbox 表
CREATE TABLE event_outbox (
    event_id         VARCHAR(64)   PRIMARY KEY,
    event_type       VARCHAR(64)   NOT NULL,
    payload          TEXT          NOT NULL,        -- JSON
    retry_count      INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMP     NOT NULL,
    published_at     TIMESTAMP
);

-- 快照表
CREATE TABLE agent_snapshot (
    snapshot_id      VARCHAR(64)   PRIMARY KEY,
    session_id       VARCHAR(64)   NOT NULL,
    trigger_type     VARCHAR(32)   NOT NULL,        -- PERIODIC/MANUAL/PRE_HITL
    snapshot_data    TEXT          NOT NULL,        -- JSON
    expires_at       TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL,
    INDEX idx_expires (expires_at)
);

-- 运行追踪表
CREATE TABLE run_trace (
    run_id           VARCHAR(64)   PRIMARY KEY,
    session_id       VARCHAR(64)   NOT NULL,
    agent_id         VARCHAR(64)   NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    duration_ms      BIGINT,
    input_tokens     INT,
    output_tokens    INT,
    strategy         VARCHAR(32),                   -- REACT/PLAN_AND_EXECUTE
    model_name       VARCHAR(128),
    step_count       INT,
    created_at       TIMESTAMP     NOT NULL
);

-- 步骤追踪表
CREATE TABLE step_trace (
    step_id          VARCHAR(64)   PRIMARY KEY,
    run_id           VARCHAR(64)   NOT NULL,
    iteration        INT           NOT NULL,
    phase            VARCHAR(32)   NOT NULL,        -- THINK/ACT/REFLECT
    decision_type    VARCHAR(32),                   -- TOOL_CALL/FINAL_ANSWER/NO_ACTION
    input_summary    TEXT,
    thinking_summary TEXT,
    output_summary   TEXT,
    duration_ms      BIGINT,
    token_count      INT,
    created_at       TIMESTAMP     NOT NULL,
    INDEX idx_run_id (run_id)
);

-- 对话记录表
CREATE TABLE conversation_record (
    record_id        VARCHAR(64)   PRIMARY KEY,
    business_id      VARCHAR(64),
    session_id       VARCHAR(64)   NOT NULL,
    agent_id         VARCHAR(64)   NOT NULL,
    message_type     VARCHAR(16)   NOT NULL,        -- USER/ASSISTANT/TOOL
    content          TEXT          NOT NULL,
    created_at       TIMESTAMP     NOT NULL,
    INDEX idx_session (session_id)
);
```

---

## 7. SPI 扩展点清单

| SPI 接口 | 默认实现 | 用途 |
|----------|----------|------|
| `Configuration` | `DefaultConfiguration` | 全局框架配置，最高级替换入口 |
| `ModelProvider` | `NovaModelProvider` | LLM 后端接入 |
| `StorageModuleFactory` | `InMemoryStorageModuleFactory` | 存储后端 |
| `MemoryModuleFactory` | `DefaultMemoryModuleFactory` | 记忆模块 |
| `QuotaService` | `NoopQuotaService` | 配额管理 |
| `AuthenticationService` | `NoopAuthenticationService` | 身份认证 |
| `AuthorizationService` | `NoopAuthorizationService` | 权限校验 |
| `SensitiveDataMasker` | `RegexSensitiveDataMasker` | 数据脱敏 |
| `ToolProvider` | — | 自定义工具注册 |
| `EmbeddingProvider` | — | 语义向量化 |

---

## 8. 治理组件交互

```
用户输入
   │
   ├─ SensitiveDataMasker.mask() ──── 脱敏处理
   │
   ├─ AuthenticationService.authenticate() ── 身份验证
   │
   ├─ AuthorizationService.authorize() ────── 权限检查
   │
   ├─ QuotaService.checkAndConsume() ───────── 配额检查
   │
   └─ 进入推理引擎
          │
          └─ 工具调用前
                 ├─ RiskLevel >= HIGH → HitlHandler.waitApproval()
                 ├─ CircuitBreaker.callAllowed() ?
                 ├─ RateLimiter.tryAcquire() ?
                 └─ 执行 → RetryPolicy.executeWithRetry()
```

---

## 9. 自进化系统设计

```
ExecutionHook (onComplete)
    │
    ▼
ExperienceCollector
    │── 成功记录：任务类型、工具序列、耗时、Token、策略
    │── 失败记录：错误类型、失败阶段、重试次数
    ▼
ExperienceAnalyzer (定时批处理)
    │── 成功率统计（按工具、按模型、按任务类型）
    │── 失败模式识别
    ▼
StrategyOptimizer
    │── 调整 maxIterations 默认值
    │── 调整工具优先级权重
    ▼
SkillEvolver
    └── 生成新工具定义草稿、速查表条目
```
