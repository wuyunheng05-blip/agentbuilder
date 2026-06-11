# AgentBuilder — 需求文档

## 项目概述

AgentBuilder 是一个面向开发者的 **AI Agent 运行时框架**（Java 8，基于 Project Reactor 响应式编程模型）。
它不是 Web 应用，而是 LLM 驱动智能体的执行引擎，提供从声明式构建、ReAct 推理、工具调用、多智能体协作到全链路可观测的完整能力。

---

## 需求列表

### REQ-01 声明式构建 API

**User Story**：作为开发者，我希望通过 Builder API 或 YAML/JSON 文件声明式地定义智能体，而不需要手动装配每个组件。

**验收标准**：

- [ ] 提供 `AgentBuilder` 流式构建器，支持链式配置（系统提示、工具列表、记忆模块、拦截器、钩子等）
- [ ] 支持从 YAML / JSON 文件加载智能体定义（`AgentDefinition`）
- [ ] `build()` 方法产出不可变的 `Agent` 实例，构建后不允许修改
- [ ] Builder 内部分两个阶段：配置收集（Builder）→ 组件装配（Assembler）
- [ ] 支持会话续接（`sessionId`）和断点恢复（从快照恢复执行上下文）

---

### REQ-02 运行时编排层

**User Story**：作为框架，我需要一个统一的运行时编排层来管理每次 `agent.run()` 的完整生命周期。

**验收标准**：

- [ ] 每次 `run()` 创建独立的 `ExecutionContext`，包含：消息历史副本、Token 预算、递归深度计数器
- [ ] 执行入口校验智能体状态机（IDLE → RUNNING → IDLE/ERROR）
- [ ] 支持前置守卫链（Guard）：递归深度检查、配额检查，不满足条件快速失败
- [ ] 输入预处理管道：敏感数据脱敏 → 记忆注入 → 上下文提供者链
- [ ] 支持快照持久化（执行过程中定期保存），允许长任务在中断后续接
- [ ] 拦截器链（Interceptor）覆盖四个 I/O 边界：Run 前/后、LLM 调用前/后、工具调用前/后
- [ ] 生命周期钩子（Hook，只读）：onStart / onStep / onComplete / onError / onCleanup

---

### REQ-03 ReAct 推理引擎

**User Story**：作为智能体，我需要通过 ReAct（Reasoning + Acting）循环自主决定何时调用工具、何时给出最终答案。

**验收标准**：

- [ ] 推理引擎采用阶段管线（Phase Pipeline）驱动：预检 → 思考 → 行动 → 终止决策 → 反思
- [ ] 每个 Phase 返回控制流指令：`CONTINUE / TERMINATE / ASYNC_BOUNDARY / RETRY_ITERATION / NEXT_ITERATION`
- [ ] 思考阶段：构建 Prompt → 调用 LLM → 解析 LLM 决策（工具调用 / 最终答案 / 无动作）
- [ ] 行动阶段：通过工具网关执行 LLM 指定的工具调用
- [ ] 终止决策：工具调用继续迭代 / 有最终答案则终止 / 无动作时注入修正提示并重试
- [ ] 反思阶段：对当前迭代步骤质量进行评估
- [ ] 预检阶段：检查 Token 预算、最大迭代数、wall-clock 超时
- [ ] 支持异步边界恢复（思考/行动为异步，回调中恢复管线推进）
- [ ] 每轮迭代记录轨迹：决策类型、工具列表、耗时、Token 消耗、是否升级为规划模式
- [ ] 自一致性验证（Self-Consistency）：低置信度答案触发多次 LLM 采样 + 多数投票

---

### REQ-04 工具系统

**User Story**：作为智能体，我需要通过统一的工具网关执行各类工具调用，并受到权限、策略和审计的保护。

**验收标准**：

- [ ] 统一工具网关（ToolGateway）：权限检查 → 策略校验 → 拦截器前置 → 参数校验 → 执行 → 重试 → 拦截器后置 → 审计记录
- [ ] 内置工具类型：
  - 规划执行（PlanAndExecute）：任务分解 + Worker 并行执行
  - 任务委派（Delegate）：单任务委派给子智能体
  - 代码沙箱（CodeSandbox）：bwrap namespace 隔离执行
  - 文件操作（FileOps）：沙箱内读写
  - 网络请求（HttpTool）：外部 API 调用
  - 知识检索（RAG）：单库 / 多知识库聚合路由
  - 人工审批（HITL）：高风险操作暂停等待人工确认
  - MCP 工具（McpTool）：Stdio 连接外部 MCP Server，动态发现工具
  - 自定义工具：通过 `ToolProvider` SPI 注册
- [ ] 策略引擎：权限控制（按实例 + 工具名）、风险等级标注、Token/时间预算分配、幂等工具结果缓存
- [ ] 高风险工具自动触发 HITL 流程（暂停执行等待人工审批后恢复）

---

### REQ-05 记忆系统

**User Story**：作为智能体，我需要三层记忆（工作/情节/语义）来管理短期对话上下文和长期知识积累。

**验收标准**：

- [ ] 三层记忆架构：
  - 工作记忆（WorkingMemory）：当前对话短期上下文
  - 情节记忆（EpisodicMemory）：按时间/重要性索引的历史事件
  - 语义记忆（SemanticMemory）：向量化知识片段，支持语义相似搜索
- [ ] 分层存储（MemoryStore）统一管理三层读写，底层委托持久化后端
- [ ] 记忆整合器（MemoryConsolidator）：异步将重要工作记忆转移到长期层
- [ ] 上下文管理器（ContextWindowManager）：滑动窗口（默认 10 轮）控制对话历史长度
- [ ] 自动压缩守卫（AutoCompressionGuard）：执行前检测超出阈值时自动压缩上下文
- [ ] Token 管理器（TokenBudgetManager）：本地 Token 用量追踪与预算控制
- [ ] BM25 检索：基于 BM25 算法的记忆条目相关性排序

---

### REQ-06 多智能体协作

**User Story**：作为开发者，我需要编排多个智能体协同完成复杂任务，支持子智能体、远程调用、工作流、自主规划四种模式。

**验收标准**：

- [ ] 子智能体（SubAgent）：封装为工具注册到编排者，支持多轮对话协议（`NEEDS_CLARIFICATION` 响应）
- [ ] 远程智能体（RemoteAgent）：通过 A2A（Agent-to-Agent）HTTP 协议跨服务调用，支持自定义认证和连接池
- [ ] 工作流编排（Workflow）：用户预定义 DAG，支持顺序、并行、条件分支、循环，节点支持 HITL 暂停
- [ ] 自主规划执行（PlanAndExecute）：内置规划工具，LLM 自行决定升级，任务分解后分配 Worker 执行
- [ ] 递归深度控制：默认 max=2，到达上限快速失败
- [ ] 子执行单元中间事件通过线程安全 sink 实时转发到父级流
- [ ] 取消传播：父级取消控制器注入子智能体上下文，支持级联取消
- [ ] 子智能体可选共享父级模型或使用独立模型

---

### REQ-07 治理与安全

**User Story**：作为平台，我需要对智能体执行进行认证、授权、配额、脱敏、韧性等全方位治理。

**验收标准**：

- [ ] 认证授权：身份认证服务（AuthenticationService）+ 权限校验服务（AuthorizationService），通过拦截器链自动生效
- [ ] 配额管理：请求频率限制 + Token 用量配额，超限快速失败返回明确错误码
- [ ] 敏感数据脱敏：正则匹配手机号、邮箱、身份证、银行卡、API Key，输入预处理阶段自动生效；支持 SPI 自定义脱敏策略
- [ ] 韧性机制：
  - 熔断器（CircuitBreaker）：模型调用连续失败时自动熔断
  - 限流器（RateLimiter）：控制并发请求速率
  - 自动重试（RetryPolicy）：可配置重试次数与退避策略
  - 超时控制：wall-clock 超时兜底
- [ ] 代码沙箱隔离：bwrap namespace（需 CAP_SYS_ADMIN）+ 多租户目录分区（tenantId/accountId）+ Skill 目录只读挂载 + 产物检测上报

---

### REQ-08 存储层

**User Story**：作为框架，我需要统一的存储模块承载所有持久化需求，并支持多种后端切换。

**验收标准**：

- [ ] `StorageModule` 为不可变数据持有者，构造后不可修改
- [ ] 包含四类存储组件：工作记忆存储、快照存储、可观测存储、可靠事件存储（Outbox）
- [ ] 支持五种后端：内存（默认/测试）、文件（本地持久化）、Redis（分布式缓存）、JDBC 数据库（生产级）、Redis+JDBC 混合策略
- [ ] 通过 SPI 覆盖 `createStorageModule()` 切换后端，业务代码零修改
- [ ] 测试时可直接注入 mock `StorageModule` 实例

---

### REQ-09 可观测性

**User Story**：作为运维/开发者，我需要对智能体每次执行进行全链路追踪、指标采集和日志记录。

**验收标准**：

- [ ] OpenTelemetry 集成：
  - Traces：每次 run 生成完整 Span 树，支持分布式追踪
  - Metrics：Token 用量、延迟分布、工具调用频率
  - OTLP 导出：标准协议导出到 Jaeger / Prometheus
- [ ] 运行轨迹存储：
  - Run 级（RunTrace）：时长、Token、状态、推理策略、模型名、步骤数
  - Step 级（StepTrace）：每迭代的输入/思考/输出/耗时
  - 只读查询接口支持审计和回放
- [ ] 流式事件帧类型：`RUN_START` / `THINKING_DELTA` / `CONTENT_DELTA` / `TOOL_CALL` / `TOOL_RESULT` / `MEMORY_RETRIEVED` / `AGGREGATE_RESULT` / `FINAL`
- [ ] 结构化日志（SLF4J + Logback）与事件帧对齐

---

### REQ-10 扩展机制与自进化

**User Story**：作为平台开发者，我需要在不修改框架核心的情况下替换任意组件，并支持智能体自我优化。

**验收标准**：

- [ ] SPI Configuration：通过 Java SPI 替换全局配置中任何默认实现（模型提供者、存储模块、记忆模块、配额服务、认证服务等）
- [ ] 拦截器（Interceptor）：四个 I/O 边界，支持短路（缓存命中/安全拒绝/限流）
- [ ] 生命周期钩子（Hook）：只读旁观者，内置快照钩子、可观测钩子、记忆钩子
- [ ] 上下文提供者（ContextProvider）：每次调用前注入上下文，调用后提取状态存储
- [ ] 自进化系统：
  - 经验收集（以钩子形式采集执行数据）
  - 经验分析（识别成功/失败模式）
  - 策略优化（根据指标自动调整参数）
  - 技能进化（从经验生成新工具定义和速查表）

---

### REQ-11 数据模型

**User Story**：作为数据层，需要定义清晰的持久化数据模型支撑所有业务。

**验收标准**：

- [ ] 检查点表（checkpoint）：会话ID、智能体状态、定义哈希、版本
- [ ] 记忆表（memory_entry）：条目ID、实例ID、记忆类型、内容、向量、重要性评分
- [ ] 事件 Outbox 表（event_outbox）：事件ID、事件类型、载荷、重试次数
- [ ] 快照表（snapshot）：快照ID、会话ID、触发类型、快照数据、过期时间
- [ ] 运行追踪表（run_trace）：运行ID、状态、时长、Token、策略、模型、步骤数
- [ ] 步骤追踪表（step_trace）：步骤ID、运行ID、阶段、类型、输入、思考、输出
- [ ] 对话记录表（conversation）：业务ID、会话ID、智能体ID、消息类型、内容
- [ ] 索引策略：所有表按业务ID建唯一索引；记忆表支持按实例+类型+重要性复合索引；快照表按过期时间索引

---

## 技术约束

| 约束项 | 要求 |
|--------|------|
| 语言版本 | Java 8（源码/目标级别），可使用 Optional、CompletableFuture、Stream |
| 响应式模型 | Project Reactor（Flux / Mono），全链路非阻塞 |
| HTTP 客户端 | OkHttp（模型调用、A2A 通信） |
| 序列化 | Jackson（JSON + YAML） |
| 缓存 | Redis / Jedis |
| 可观测性 | OpenTelemetry SDK + OTLP |
| 脚本引擎 | Nashorn（JavaScript 沙箱） |
| 日志 | SLF4J + Logback |
| 测试框架 | JUnit 5 + Mockito + jqwik（属性测试） |
| 内部依赖 | ai-nova-core / ai-nova-agent / ai-nova-graph |
