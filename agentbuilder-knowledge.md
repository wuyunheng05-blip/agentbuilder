# AgentBuilder 知识手册

> 这份文档是为了让你在面试中能流畅、自信地讲解项目的每一个技术细节。
> 每个章节按"是什么 → 为什么这样做 → 怎么实现 → 可能被追问什么"的结构组织。

---

## 目录

1. [整体架构：为什么这样分层？](#1-整体架构)
2. [响应式编程：Flux 和 Mono 是怎么用的？](#2-响应式编程)
3. [ReAct 推理引擎：Phase Pipeline 是什么？](#3-react-推理引擎)
4. [工具系统：网关链路与 HITL](#4-工具系统)
5. [三层记忆：为什么要分三层？](#5-三层记忆系统)
6. [多智能体：四种模式怎么选？](#6-多智能体协作)
7. [治理安全：熔断器、限流器怎么实现？](#7-治理与安全)
8. [SPI 扩展机制：Java SPI 怎么工作？](#8-spi-扩展机制)
9. [存储层：不可变 StorageModule 的设计](#9-存储层设计)
10. [可观测性：OpenTelemetry 怎么用？](#10-可观测性)
11. [自进化系统：经验怎么驱动优化？](#11-自进化系统)
12. [设计决策问答：面试高频追问](#12-面试高频追问)

---

## 1. 整体架构

### 是什么

AgentBuilder 是一个 **8 层主执行链路 + 4 个横切关注点** 的分层框架：

```
API 层              → 对外接口，Flux<AgentEvent> run()
构建与装配层         → Builder 收集配置 → Assembler 装配组件 → 不可变 Agent 实例
运行时编排层         → ExecutionContext、拦截器链、守卫链、生命周期钩子
推理引擎层           → ReAct Phase Pipeline（预检→思考→行动→终止→反思）
工具系统             → ToolGateway 统一入口，9 类内置工具
记忆系统             → 三层记忆（Working/Episodic/Semantic）
模型抽象层           → ModelProvider SPI，韧性代理
基础设施层           → StorageModule、事件总线、线程池、SPI 加载

横切：多智能体 / 治理安全 / 可观测性 / 自进化
```

### 为什么这样分层

**分层的核心驱动是关注点分离和可替换性**。每层只做一件事，任意层都可以通过 SPI 替换实现：
- 不想用内存存储？替换 `StorageModuleFactory`
- 想接入新的 LLM？替换 `ModelProvider`
- 想自定义认证？替换 `AuthenticationService`

框架核心代码 **一行不改**，业务侧通过 `ServiceLoader` 注入新实现即可。

### Builder 两阶段模式

```
AgentBuilder（收集配置，可变）
    ↓
AgentConfig（配置快照，不可变值对象）
    ↓
AgentAssembler（依赖注入、组件装配）
    ↓
AgentRuntime（运行时实例，不可变）
```

**为什么要两阶段？** 如果 `build()` 和 `run()` 混在一起，组件装配会在每次调用时重复发生，且无法保证线程安全。两阶段使 `AgentRuntime` 构造后完全不可变，可以安全地跨线程共享，同时测试时可以精确控制装配过程。

---

## 2. 响应式编程

### Flux 和 Mono 是什么

Project Reactor 是 Java 的响应式编程库，实现了 Reactive Streams 规范：
- `Mono<T>`：0 或 1 个元素的异步序列（类比 CompletableFuture）
- `Flux<T>`：0 到 N 个元素的异步序列（类比异步 Stream）

### 在项目里怎么用

**智能体执行结果**：`Flux<AgentEvent>`，消费者订阅后按需拉取（背压）

```java
agent.run(input)
    .filter(e -> e.getType() == CONTENT_DELTA)
    .map(e -> (String) e.getPayload())
    .subscribe(System.out::print);
```

**工具执行**：`Mono<ToolResult>`，一次工具调用返回一个结果

**内存存储**：`Flux<MemoryEntry> query()`，支持流式消费大量历史记录

### 为什么用响应式而不是传统阻塞式

1. **LLM 调用天然是流式的**（SSE/Server-Sent Events），响应式模型可以逐 token 实时转发给前端，用户不需要等全部生成完才看到结果
2. **背压控制**：如果消费者（如前端 WebSocket）处理慢，Flux 会自动控制生产速度，不会 OOM
3. **非阻塞**：`Schedulers.boundedElastic()` 线程池处理 I/O，不占用大量线程

### FluxSink 的作用

`FluxSink<AgentEvent>` 是命令式代码向响应式流桥接的工具：

```java
Flux.create(sink -> {
    // 推理循环里发出事件
    sink.next(AgentEvent.of(THINKING_DELTA, content));
    // 完成
    sink.complete();
    // 或者出错
    sink.error(new RuntimeException("LLM timeout"));
})
```

子智能体的事件通过同一个 sink 转发到父级流，消费者只需订阅顶层 Flux 就能看到整棵调用树的所有事件。

---

## 3. ReAct 推理引擎

### ReAct 是什么

ReAct（Reasoning + Acting）是一种 LLM 推理范式，让模型在 **思考（Reason）** 和 **行动（Act）** 之间交替迭代：

```
Thought: 我需要查询天气
Action: call weather_tool(city="北京")
Observation: 北京今天 25°C，晴
Thought: 已有答案
Final Answer: 北京今天 25°C，晴
```

### Phase Pipeline 是什么

项目没有用 if-else 实现 ReAct 循环，而是用 **阶段管线（Phase Pipeline）** 模式：

```java
phases = [PreCheckPhase, ThinkPhase, ActionPhase, TerminationPhase, ReflectionPhase]

while (iteration <= maxIterations) {
    for (Phase phase : phases) {
        PhaseResult result = phase.execute(ctx, sink);
        switch (result.action) {
            case CONTINUE:         // 继续下一个 Phase
            case TERMINATE:        // 完成，退出所有循环
            case ASYNC_BOUNDARY:   // Phase 自己管异步回调，当前线程退出
            case RETRY_ITERATION:  // 本轮重新开始
            case NEXT_ITERATION:   // 进入下一轮
        }
    }
}
```

**好处**：
- 每个 Phase 只关注自己的逻辑，单元测试独立
- 控制流语义显式表达（PhaseAction 枚举），不隐藏在 boolean 返回值里
- 新增 Phase 只需实现接口并插入列表，不改已有代码

### 各 Phase 详解

| Phase | 做什么 | 可能返回的 Action |
|-------|--------|-----------------|
| PreCheckPhase | 检查 Token 预算/超时/迭代数/取消状态 | CONTINUE 或 TERMINATE |
| ThinkPhase | 构建 Prompt → 调用 LLM → 解析决策 | ASYNC_BOUNDARY（LLM 是异步的） |
| ActionPhase | 执行 LLM 决定的工具调用 | ASYNC_BOUNDARY（工具是异步的） |
| TerminationPhase | 判断是否终止：最终答案/继续/修正 | TERMINATE / NEXT_ITERATION / RETRY_ITERATION |
| ReflectionPhase | 评估步骤质量，记录轨迹 | CONTINUE |

### 异步边界是个什么问题

LLM 调用和工具调用是异步的（返回 `Mono/Flux`），但 Phase Pipeline 是同步 for 循环。

解决方式：ThinkPhase 在发起异步调用后立即返回 `ASYNC_BOUNDARY`，编排器退出当前线程。LLM 回调触发时，**在回调中重新恢复 Phase 推进**（调用 `continueFromPhase(actionPhase)`），相当于把同步管线切成了"协程"式的分段执行。

### 自一致性验证

当 LLM 给出答案但置信度低于阈值（默认 0.7）时，触发 Self-Consistency：

```
同一个 Prompt → LLM 采样 1
同一个 Prompt → LLM 采样 2   → 多数投票 → 最终答案
同一个 Prompt → LLM 采样 3
```

用 `Flux.merge(call1, call2, call3)` 并行发起 3 次调用，收集结果做多数投票。如果 3 次结果都不一致，降低最终 `confidenceScore`。

**面试角度**：这其实是对模型不稳定性的工程补偿，不依赖模型本身的置信度输出，而是通过多次采样统计来估计可靠性。

---

## 4. 工具系统

### 工具网关链路

所有工具调用必须经过 ToolGateway，不允许直接调用：

```
权限检查（AuthorizationService）
    ↓
策略校验（PolicyEngine）：风险等级/预算
    ↓
拦截器前置（InterceptorChain.beforeToolCall）← 可短路（缓存命中）
    ↓
参数 JSON Schema 校验
    ↓
执行（RetryPolicy 包裹）
    ↓
拦截器后置（InterceptorChain.afterToolCall）
    ↓
审计记录（AuditLogger）
```

**为什么要统一网关？** 如果各处直接调用工具，权限、审计、重试、限流等横切逻辑会散落各地难以维护。网关模式把这些关注点集中在一处。

### 工具的 RiskLevel

每个工具有风险级别注解：
- `LOW`：读取天气、搜索文档 → 直接执行
- `MEDIUM`：发送邮件、写文件 → 执行但记录详细审计
- `HIGH`：调用外部支付 API → 触发 HITL，暂停等待人工审批
- `CRITICAL`：删除数据库记录 → 强制 HITL，不可绕过

### HITL（Human-in-the-Loop）机制

```
工具调用到达策略引擎
    ↓
RiskLevel = CRITICAL → 发布"待审批"事件 → 暂停执行流
    ↓
外部审批系统（或控制台输入） → ApprovalService.approve(callId)
    ↓
恢复执行 or 拒绝（返回 ToolResult.error）
```

**实现关键**：用 `Mono.create()` 创建一个挂起的 Mono，审批结果到达时 `monoSink.success(result)` 触发恢复。这样整个响应式链路不会阻塞线程，只是"等待"事件。

### MCP 工具

MCP（Model Context Protocol）是一种让 LLM 动态发现外部工具的协议：

1. 通过 `ProcessBuilder` 启动 MCP Server 进程（Stdio 模式）
2. 通过标准输入输出发送 JSON-RPC：`tools/list` 获取工具列表，`tools/call` 执行工具
3. 将发现的工具动态注册到 `ToolRegistry`

**面试点**：MCP 相当于工具的"插件市场"，不需要编译时集成，运行时即插即用。

### 幂等工具缓存

```java
if (tool.isIdempotent()) {
    Optional<ToolResult> cached = cache.get(cacheKey(call));
    if (cached.isPresent()) return Mono.just(cached.get()); // 短路
}
```

`cacheKey` = `toolName + sortedArguments.toString()`。用于搜索、查询类工具，避免重复调用消耗 API 配额。

---

## 5. 三层记忆系统

### 为什么要三层

人类的记忆也是分层的，这套设计来自认知科学：

| 层次 | 类比 | 存储什么 | 检索方式 |
|------|------|---------|---------|
| 工作记忆 | 短时记忆 | 当前对话上下文（最近 10 轮） | 直接读取 |
| 情节记忆 | 长时记忆-事件 | 历史对话中的重要事件（按时间+重要性） | BM25 关键词检索 |
| 语义记忆 | 长时记忆-知识 | 向量化知识片段（含 embedding） | 余弦相似度检索 |

**工程价值**：不是所有历史信息都要放进 LLM 的上下文窗口（有 Token 上限），通过三层记忆可以按需检索最相关的内容注入，大幅降低 Token 消耗。

### 记忆整合器

每次 `run()` 完成后，`MemoryConsolidationHook.onComplete()` 异步执行：

```
查询本次工作记忆
    ↓ importanceScore >= 0.7
保存到情节记忆（带时间戳）
    ↓ 如果有 EmbeddingProvider
生成 embedding → 保存到语义记忆
```

**重要性评分规则**：
- 工具成功执行结果：0.8
- 最终答案：0.9  
- 普通消息：0.5

这个过程在 `Schedulers.boundedElastic()` 线程上异步执行，不阻塞主流程返回。

### BM25 是什么

BM25（Best Match 25）是信息检索领域的经典算法，比 TF-IDF 更精准：

```
BM25(q, d) = Σ IDF(qi) × (freq(qi,d) × (k1+1)) / (freq(qi,d) + k1 × (1 - b + b × |d|/avgdl))
```

- `freq(qi, d)`：词 qi 在文档 d 中的频次
- `|d|/avgdl`：文档长度归一化（避免长文档占便宜）
- `k1, b`：可调参数

**面试点**：用于情节记忆检索，比简单 `contains` 匹配效果好，不需要 embedding 模型，计算量小。

### 上下文窗口与自动压缩

```
消息历史长度估算（length/4 近似 token 数）
    ↓ 超过 maxContextTokens × 0.8
触发自动压缩：
    - 取最老 K 条消息
    - 调用 LLM 生成摘要
    - 用一条摘要消息替换 K 条原始消息
```

**面试点**：这是解决"长对话上下文溢出"的工程方案，在保留语义的前提下减少 token 占用。代价是增加了一次 LLM 调用，所以设置了 0.8 的触发阈值，不会频繁压缩。

---

## 6. 多智能体协作

### 四种模式对比

| 模式 | 何时用 | 实现方式 | 关键特征 |
|------|--------|---------|---------|
| SubAgent | 任务可以委派给专用智能体 | 封装为 Tool，LLM 决定调用时机 | 灵活，LLM 自主决策 |
| RemoteAgent | 被调智能体在另一个服务里 | A2A HTTP + SSE 协议 | 跨服务边界 |
| Workflow | 流程预先确定，不需要 LLM 编排 | YAML DAG + 拓扑排序执行 | 确定性，可审计 |
| PlanAndExecute | 复杂任务，需要动态分解 | LLM 生成计划 → Worker 并行执行 | 灵活，适合不确定任务 |

### SubAgent 封装原理

子智能体被包装成一个普通工具：

```java
class SubAgentTool implements Tool {
    private final Agent subAgent;

    public Mono<ToolResult> execute(ToolCall call, ExecutionContext ctx) {
        ExecutionContext childCtx = ctx.nested(); // depth + 1
        // 注入父级取消令牌（级联取消）
        childCtx.setCancellationToken(ctx.getCancellationToken());
        
        return subAgent.run(subInput)
            .doOnNext(event -> parentSink.next(event))  // 事件转发到父级流
            .filter(e -> e.getType() == FINAL)
            .map(e -> ToolResult.of(e.getPayload()))
            .last();
    }
}
```

**递归深度控制**：`RecursionDepthGuard` 在每次 run 前检查 `ctx.recursionDepth`，超过 2 时快速失败。防止 A 调 B 调 C 调 A 的无限递归。

### RemoteAgent 的 A2A 协议

```
编排者 Agent → POST /a2a/run {input} → 远程服务
             ← {runId: "abc123"}
             → GET /a2a/stream/abc123  (SSE 连接)
             ← data: {"type":"THINKING_DELTA","payload":"..."}
             ← data: {"type":"TOOL_CALL","payload":"..."}
             ← data: {"type":"FINAL","payload":"..."}
```

基于 OkHttp SSE，`EventSource` 逐行解析 JSON 转为本地 `AgentEvent`，再转发给父级流。

### Workflow DAG 执行

```yaml
nodes:
  - id: A              # 无依赖，可以立即执行
  - id: B              # 无依赖，可以立即执行
  - id: C
    dependsOn: [A, B]  # 等 A 和 B 都完成才能执行
```

执行逻辑：
1. 拓扑排序，找出入度为 0 的节点
2. `Flux.merge(executeNode(A), executeNode(B))` 并行执行
3. A 和 B 完成后，C 的依赖满足，进入执行队列
4. 变量替换：`{{A.output}}` 替换为 A 节点的执行结果

---

## 7. 治理与安全

### 熔断器（CircuitBreaker）

**问题**：LLM 服务偶尔会不稳定，如果每次都等超时才失败，会大量占用线程和时间。

**熔断器三态**：

```
CLOSED（正常）
    ↓ 连续失败 N 次（或失败率超阈值）
OPEN（熔断）→ 所有请求立即拒绝，不调用 LLM
    ↓ 等待 cooldown 时间（如 30s）
HALF_OPEN（探测）→ 允许 1 个请求试探
    ↓ 成功 → CLOSED
    ↓ 失败 → OPEN
```

**实现**：基于滑动窗口统计失败次数，`AtomicReference<State>` 保证状态转换线程安全。

```java
public <T> Mono<T> protect(Supplier<Mono<T>> operation) {
    if (state == OPEN) return Mono.error(new CircuitBreakerOpenException());
    return operation.get()
        .doOnSuccess(r -> recordSuccess())
        .doOnError(e -> recordFailure());
}
```

### 限流器（令牌桶算法）

**令牌桶**：桶容量 N，每秒补充 R 个令牌，请求到来消耗 1 个令牌，桶空则拒绝。

```java
// 基于 AtomicLong 的无锁实现
public boolean tryAcquire() {
    long now = System.currentTimeMillis();
    long tokens = Math.min(capacity, storedTokens + (now - lastRefillTime) * refillRate / 1000);
    if (tokens >= 1) {
        storedTokens = tokens - 1;
        lastRefillTime = now;
        return true;
    }
    return false;
}
```

**面试点**：令牌桶允许突发流量（桶里积累的令牌），漏桶（固定速率处理）不允许突发。LLM 场景更适合令牌桶，因为用户请求天然有突发性。

### 指数退避重试

```
第 1 次失败 → 等 1s 重试
第 2 次失败 → 等 2s 重试
第 3 次失败 → 等 4s 重试
第 4 次失败 → 永久失败，抛异常
```

**为什么指数退避？** 如果服务刚好在过载，立即重试会加剧压力。指数退避给服务恢复的时间，同时加入随机 jitter（±10% 随机偏移），避免多个客户端同时重试产生"惊群效应"。

### 敏感数据脱敏

输入预处理阶段，在进入推理引擎前对用户输入做正则替换：

```java
private static final Map<String, String> PATTERNS = Map.of(
    "手机号",   "1[3-9]\\d{9}",
    "身份证",   "\\d{17}[0-9X]",
    "邮箱",     "[\\w.]+@[\\w.]+\\.[a-z]{2,}",
    "银行卡",   "\\d{16,19}",
    "API Key",  "[A-Za-z0-9]{32,}"
);
// 统一替换为 [MASKED]
```

**面试点**：在输入侧脱敏（而不是输出侧），确保敏感数据不会进入 LLM 的上下文，也不会出现在日志里。通过 SPI 可以扩展业务特有的脱敏规则（如合同编号、内部 ID 等）。

---

## 8. SPI 扩展机制

### Java SPI 是什么

SPI（Service Provider Interface）是 Java 内置的插件机制：

1. 在 jar 包的 `META-INF/services/` 目录下创建一个文件，文件名是接口全类名
2. 文件内容是实现类的全类名
3. `ServiceLoader.load(接口.class)` 自动发现并加载所有实现

```
agentbuilder-core/src/main/resources/
└── META-INF/services/
    └── com.example.agentbuilder.api.spi.ModelProvider
        → 内容：com.example.agentbuilder.core.nova.NovaModelProvider
```

### AgentBuilder 的 10 个 SPI 点

| SPI 接口 | 默认实现 | 业务可替换的场景 |
|----------|----------|----------------|
| `Configuration` | DefaultConfiguration | 整体配置替换入口 |
| `ModelProvider` | NovaModelProvider | 接入 OpenAI / Claude / 自建模型 |
| `StorageModuleFactory` | InMemoryStorageModuleFactory | 切换生产数据库 |
| `MemoryModuleFactory` | DefaultMemoryModuleFactory | 自定义记忆策略 |
| `QuotaService` | NoopQuotaService | 接入计费系统 |
| `AuthenticationService` | NoopAuthenticationService | 接入 OAuth / JWT |
| `AuthorizationService` | NoopAuthorizationService | 接入 RBAC 权限系统 |
| `SensitiveDataMasker` | RegexSensitiveDataMasker | 自定义脱敏规则 |
| `ToolProvider` | （无默认）| 注册业务工具 |
| `EmbeddingProvider` | （无默认）| 接入向量化服务 |

### 面试角度

**Q：为什么不用 Spring 的依赖注入？**

框架定位是 Java 8 原生库，不强依赖任何容器。用 SPI 可以在 Spring 应用里使用，也可以在命令行工具、Quarkus、微服务里使用，框架本身不感知环境。

**Q：SPI 和工厂模式有什么区别？**

工厂模式在编译时确定创建哪个实现；SPI 在运行时由类路径决定。SPI 实现了真正的"开闭原则"：扩展不需要修改框架源码。

---

## 9. 存储层设计

### StorageModule 不可变模式

```java
public final class StorageModule {
    private final MemoryStore memoryStore;
    private final SnapshotStore snapshotStore;
    private final ObservabilityStore observabilityStore;
    private final OutboxStore outboxStore;
    
    // 构造后所有字段只读，无 setter
}
```

**为什么不可变？**
1. 天然线程安全，多个 `run()` 并发时共享同一个 `StorageModule` 不需要加锁
2. 测试时可以直接 `new StorageModule(mockMemoryStore, mockSnapshotStore, ...)` 注入 mock
3. 防止运行时偷偷修改存储后端导致难以追踪的 bug

### Outbox 事件投递

解决分布式场景下"执行完成了但事件没发出去"的问题：

```
业务操作 + 写 Outbox 表（同一事务）
    ↓
定时轮询 Outbox 表
    ↓
投递到消息队列 / 事件总线
    ↓
标记 published_at
```

**面试点**：这是标准的 Outbox Pattern，保证"至少一次投递"（at-least-once）。消费方需要做幂等处理。用 Redis List 的 `LPUSH/BRPOP` 实现时不需要额外的轮询，Redis 原子操作保证消息不丢失。

### 五种存储后端的选择时机

| 后端 | 场景 | 特点 |
|------|------|------|
| 内存 | 单元测试、本地开发 | 零配置，重启丢失 |
| 文件 | 单机生产、嵌入式场景 | 无外部依赖，但不支持并发 |
| Redis | 分布式缓存层、快速读写 | 速度快，数据有 TTL，适合快照/会话 |
| JDBC | 生产级持久化 | 事务支持，可靠 |
| Redis+JDBC | 高并发生产环境 | 热数据 Redis，冷数据落库 |

---

## 10. 可观测性

### OpenTelemetry 三大信号

**Traces（分布式追踪）**：记录一次 `run()` 的完整调用链
```
Span: agent.run (根 Span)
├── Span: agent.step.PreCheck
├── Span: agent.step.Think → 属性: model=gpt-4, inputTokens=1024
├── Span: agent.step.Action → 属性: tool=weather_api, duration=234ms
└── Span: agent.step.Termination
```

**Metrics（指标）**：
- `agent_run_total` Counter：总调用次数，按 agentId/status 分标签
- `llm_call_duration_ms` Histogram：LLM 延迟分布（p50/p95/p99）
- `token_usage_total` Counter：按模型名统计 Token 消耗
- `tool_call_total` Counter：按工具名统计调用次数

**OTLP Export（导出）**：标准 gRPC/HTTP 协议推送到 Jaeger（追踪）或 Prometheus（指标）

### 为什么用 OpenTelemetry 而不是自定义日志

1. **可移植性**：OTLP 是厂商中立的协议，可以无缝切换到 Datadog、Dynatrace、Grafana Cloud
2. **结构化数据**：Span 的 Attribute 是强类型键值对，比字符串日志更易查询
3. **关联性**：TraceId 贯穿所有日志和 Span，可以精确还原一次 run 的完整链路

### 8 种流式事件帧

按照时间序排列，消费者收到的事件序列如下：

```
RUN_START          → run 开始，携带 runId/sessionId
THINKING_DELTA     → LLM 流式输出的思考片段（可能多次）
CONTENT_DELTA      → LLM 流式输出的内容片段（可能多次）
TOOL_CALL          → 工具调用请求（toolName + arguments）
TOOL_RESULT        → 工具执行结果
MEMORY_RETRIEVED   → 从记忆检索到相关内容
AGGREGATE_RESULT   → 自一致性验证的聚合结果（如果触发了）
FINAL              → 最终结果（status + content + tokenUsage + confidenceScore）
ERROR              → 出错（包含错误信息和 AgentStatus）
```

**面试点**：前端 WebSocket 可以直接订阅这个 `Flux`，实现"打字机效果"（逐字显示）。

---

## 11. 自进化系统

### 整体流程

```
每次 run() 完成
    ↓ ExperienceCollector（钩子）
记录：任务类型 / 工具调用序列 / 迭代次数 / Token / 最终状态
    ↓ 写入 OutboxStore（异步）
ExperienceAnalyzer（定时批处理，如每小时）
    ↓
统计：工具调用成功率 / 按任务类型的平均迭代数 / 失败模式
    ↓
StrategyOptimizer
    ↓ 输出 StrategyAdjustment
调整：maxIterations 默认值 / 工具优先级权重
    ↓
SkillEvolver
    ↓
生成速查表（存入语义记忆）/ 生成新工具定义草稿
```

### 面试点

这个系统的核心思想是 **"从实践中学习"**：
- 不是通过重新训练模型（成本太高）
- 而是调整框架层面的参数和提示词
- 把成功的工具调用模式存入语义记忆，让未来的推理能优先参考

本质上是一个小型的 **强化学习反馈回路**，只不过"策略"是框架参数，"奖励信号"是任务成功/失败。

---

## 12. 面试高频追问

### Q1：为什么选 Java 8 而不是更新版本？

Java 8 是企业环境（特别是大厂内部）最广泛部署的版本。框架使用了 Java 8 全部现代特性（`Optional`、`Stream`、`CompletableFuture`、函数式接口），代码质量不低于 Java 11+。更重要的是，目标用户（企业开发者）的运行环境很多还停在 Java 8，框架不应该强制升级。

### Q2：Phase Pipeline 里的 ASYNC_BOUNDARY 怎么避免死锁？

关键在于 Phase 返回 `ASYNC_BOUNDARY` 后，**当前线程立即返回**，不持有任何锁。异步回调在 Reactor 的 `Schedulers.boundedElastic()` 线程上执行，不依赖调用线程。整条链路是无锁的，不会死锁。

### Q3：多个 run() 并发调用同一个 Agent 实例会怎样？

`AgentStateMachine` 用 `AtomicReference<AgentStatus>` 做 CAS 操作：

```java
boolean transitioned = status.compareAndSet(IDLE, RUNNING);
if (!transitioned) throw new AgentBusyException("Agent is already running");
```

第二个 `run()` 会立即抛出 `AgentBusyException`，不会进入推理循环。一个 Agent 实例在任意时刻只有一个 `run()` 在执行。如果需要并发，应该创建多个 Agent 实例。

### Q4：快照断点恢复是怎么实现的？

```
定期快照（SnapshotHook，每 N 轮迭代触发）：
    序列化 ExecutionContext（消息历史 + Token 预算 + 当前迭代数）
    → JSON 存入 SnapshotStore（key: snapshotId）

恢复（agent.resume(snapshotId, input)）：
    从 SnapshotStore 读取 snapshot
    → 反序列化为 ExecutionContext
    → 从保存的迭代数继续执行 Phase Pipeline
```

**面试点**：快照的触发时机设计很重要。在 HITL 审批前、高风险工具执行前必须保存快照，这样即使审批超时或服务重启，也能从检查点续接。

### Q5：记忆检索怎么避免"记忆污染"（检索到不相关内容）？

三道防线：
1. **importanceScore 过滤**：只检索重要性 ≥ 阈值的条目
2. **BM25/余弦相似度阈值**：只取相似度最高的 topK 条，丢弃低相关条目
3. **ContextProvider 注入**：注入的记忆标记为 `[MEMORY]` 前缀，让 LLM 知道这是历史信息，而不是当前指令

### Q6：熔断器打开后，如何判断服务恢复了？

HALF_OPEN 状态下，允许 **1 个探测请求** 通过：
- 成功 → 滑动窗口清零，回到 CLOSED
- 失败 → 重置 cooldown 计时器，回到 OPEN 继续等待

为什么只放 1 个而不是多个？因为服务刚恢复时可能还很脆弱，多个并发探测可能让它再次崩溃。

### Q7：工作流 DAG 的循环节点怎么防止无限循环？

两个机制：
1. **最大循环次数**（`maxLoopIterations`）：YAML 定义里可以配置
2. **总体超时**（`workflowTimeout`）：整个工作流的 wall-clock 时间上限

```yaml
nodes:
  - id: retry_loop
    type: while
    condition: "{{lastResult.success}} == false"
    maxIterations: 5    # 超过 5 次强制退出
    timeout: 30000      # 30 秒超时
```

### Q8：SPI 加载顺序有保证吗？如果有多个实现怎么选？

`ServiceLoader` 遍历 classpath，顺序由 jar 包加载顺序决定，**不保证固定顺序**。

解决方式：
1. `Configuration` SPI 只允许一个实现，多个时用优先级注解（`@Priority`）选最高优先级的
2. `ToolProvider` 允许多个实现，全部加载，取并集注册到 `ToolRegistry`

---

## 总结：面试自我介绍时的关键词

> "我设计并实现了 AgentBuilder，一个 Java 8 + Project Reactor 的 AI Agent 执行引擎。核心是 **ReAct Phase Pipeline**，把推理循环分解为可组合的阶段，每个阶段通过控制流指令驱动状态机，支持异步边界恢复。在工具系统上设计了统一网关，集成权限、策略、HITL 审批和幂等缓存。记忆层借鉴认知科学的三层模型，用 BM25 和向量检索按需注入上下文。治理方面实现了熔断器/限流/重试三板斧和全链路 OpenTelemetry 追踪。整个框架通过 SPI 机制提供 10 个扩展点，框架核心代码不感知具体实现。"

这段话大约 30 秒，覆盖了架构思路、核心技术、工程亮点，能引导面试官往你熟悉的方向追问。
