# 个人简历

---

## 基本信息

**姓名**：[你的姓名]  
**电话**：[手机号]  
**邮箱**：[邮箱地址]  
**GitHub**：[github.com/yourname]  
**求职意向**：Java 后端工程师 / 基础架构工程师 / AI 平台工程师

---

## 技能栈

- **语言**：Java 8/11/17、SQL
- **框架与中间件**：Project Reactor、Spring Boot、OkHttp、Jackson、Redis/Jedis
- **AI/LLM**：LLM 接入与编排、ReAct 推理范式、RAG 检索增强生成、MCP 协议、Embedding 向量检索
- **可观测性**：OpenTelemetry（Traces/Metrics/OTLP）、SLF4J + Logback、Jaeger、Prometheus
- **存储**：MySQL（JDBC）、Redis、文件存储、多后端策略
- **架构模式**：SPI 扩展机制、Builder 模式、拦截器链、发布-订阅、Outbox 可靠事件投递
- **工程实践**：JUnit 5、Mockito、jqwik 属性测试、Maven 多模块、CI/CD

---

## 项目经历

### AgentBuilder — AI Agent 运行时框架

**角色**：独立设计者 & 实现者  
**时间**：2025.xx — 至今  
**技术栈**：Java 8 · Project Reactor · OkHttp · Jackson · Redis · OpenTelemetry · Java SPI

#### 项目背景

独立设计并实现了一个企业级 **AI Agent 运行时框架**，面向开发者提供从智能体声明式构建、ReAct 推理执行、工具调用、多智能体协作到全链路可观测的完整能力。框架不是传统 Web 应用，而是以响应式编程模型为核心的 LLM 驱动执行引擎。

#### 架构设计亮点

**分层架构，共 8 层主执行链路 + 4 个横切关注点（多智能体 / 治理 / 可观测性 / 自进化）**，模块间通过 Java SPI 解耦，支持任意层的热替换。

采用 Maven 多模块项目结构（10 个子模块），各模块职责单一：

```
agentbuilder-api         → 接口与数据模型合约
agentbuilder-core        → 运行时编排 + 推理引擎
agentbuilder-memory      → 三层记忆系统
agentbuilder-tools       → 内置工具实现
agentbuilder-multi-agent → 多智能体协作
agentbuilder-governance  → 治理与安全
agentbuilder-observability → OpenTelemetry 可观测
agentbuilder-storage     → 多后端存储层
agentbuilder-evolution   → 自进化系统
agentbuilder-test        → 集成测试
```

#### 核心技术实现

**① ReAct 推理引擎（Phase Pipeline）**

设计了基于阶段管线的推理编排器，替代传统 if-else 控制流。每个 Phase（预检/思考/行动/终止决策/反思）返回类型化控制流指令（`CONTINUE / TERMINATE / ASYNC_BOUNDARY / RETRY_ITERATION / NEXT_ITERATION`），编排器根据指令驱动状态机，支持异步边界恢复（LLM/工具调用完成后回调中恢复管线）。

实现了自一致性验证（Self-Consistency）：对置信度低于阈值的答案并行发起 N 次 LLM 采样，通过多数投票确认最终输出，提升推理可靠性。

**② 响应式流设计（Project Reactor）**

全链路以 `Flux<AgentEvent>` 作为执行结果，8 种事件帧（`RUN_START / THINKING_DELTA / TOOL_CALL / TOOL_RESULT / FINAL` 等）实时流式推送给消费者，支持背压控制。子智能体中间事件通过线程安全 `FluxSink` 实时转发到父级流，实现嵌套调用的透明可见性。

**③ 工具网关（ToolGateway）**

实现统一工具调用链路：`权限检查 → 策略校验 → 拦截器前置 → 参数校验 → 执行 → 重试 → 拦截器后置 → 审计记录`。内置 9 类工具（HTTP 调用/RAG 检索/代码沙箱/HITL 审批/MCP 客户端等），支持 SPI 自定义扩展。对幂等工具实现结果缓存，高风险工具（`RiskLevel.HIGH/CRITICAL`）自动触发 Human-in-the-Loop 暂停审批流程。

**④ 三层记忆系统**

设计了认知科学启发的三层记忆模型：
- **工作记忆**：当前对话短期上下文，滑动窗口（默认 10 轮）管理
- **情节记忆**：按时间和重要性索引的历史事件，BM25 算法检索
- **语义记忆**：向量化知识片段，余弦相似度检索

实现了异步记忆整合器（`MemoryConsolidator`）：run 完成后将重要性评分 ≥ 0.7 的工作记忆异步升级到长期层，不阻塞主流程。上下文超限时自动调用 LLM 做历史摘要压缩。

**⑤ 多智能体协作（4 种模式）**

- **SubAgent**：子智能体封装为工具，编排者 LLM 自主决定调用时机，支持多轮澄清协议
- **RemoteAgent**：通过 A2A（Agent-to-Agent）HTTP + SSE 协议跨服务调用，带连接池和自定义认证
- **Workflow**：YAML 定义的 DAG 工作流，支持顺序/并行/条件分支/循环，节点支持 HITL 暂停
- **PlanAndExecute**：LLM 自主判断任务复杂度后升级执行模式，任务分解后分配 Worker 并行执行

实现了递归深度控制（默认 max=2）和取消传播（父级 `CancellationToken` 注入子上下文，级联取消）。

**⑥ 治理与安全**

- **韧性机制**：熔断器（滑动窗口统计 + CLOSED/OPEN/HALF_OPEN 状态机）+ 令牌桶限流器 + 指数退避重试，统一封装为 `ResilienceModelProxy` 代理层
- **数据治理**：正则匹配 5 类敏感数据（手机/邮箱/身份证/银行卡/API Key）自动脱敏，支持 SPI 自定义策略
- **代码沙箱**：bwrap namespace 隔离 + 多租户目录分区（按 tenantId/accountId），Skill 目录只读挂载

**⑦ 可观测性（OpenTelemetry）**

每次 `run()` 生成完整 Span 树（含子 Span per Phase），通过 OTLP 协议导出到 Jaeger/Prometheus。实现 Run 级和 Step 级两层轨迹存储，记录每迭代的决策类型、工具调用、耗时、Token 消耗，支持只读审计查询接口。

**⑧ SPI 扩展机制**

定义 10 个 SPI 扩展点（`Configuration / ModelProvider / StorageModuleFactory / QuotaService / AuthenticationService` 等），通过 `ServiceLoader` 加载，框架核心代码零改动即可替换任意组件。存储模块（`StorageModule`）为不可变对象，支持内存/文件/Redis/JDBC/混合 5 种后端，通过 SPI 工厂方法切换。

**⑨ 自进化系统**

以钩子形式异步采集每次执行经验，定时批处理识别成功/失败模式，自动调整推理策略参数，将高频成功工具调用序列生成速查表存入语义记忆。

#### 数据模型设计

设计了 7 张核心数据表：检查点表（断点恢复）、记忆条目表（三层记忆持久化，含 embedding 字段）、事件 Outbox 表（可靠事件投递）、快照表（执行状态快照）、运行追踪表、步骤追踪表、对话记录表。对记忆表设计了按实例+类型+重要性的复合索引，快照表按过期时间索引支持定期清理。

#### 测试策略

- 单元测试：JUnit 5 + Mockito，覆盖各 Phase、拦截器链、守卫链、记忆检索
- 端到端测试：Mock `ModelProvider` 驱动，覆盖单轮问答/工具链/断点恢复/HITL/熔断/Token 超限 8 个场景
- 属性测试：jqwik 生成 100+ 随机工具调用序列，验证事件流单调递增无乱序

---

## 教育背景

**[大学名称]** — [专业]  
[入学年份] — [毕业年份]

---

## 其他

- 熟悉 AI Agent 领域前沿：LangChain、AutoGPT、Microsoft AutoGen、A2A 协议、MCP 协议
- 关注响应式编程和高并发系统设计
