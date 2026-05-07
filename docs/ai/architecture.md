# 架构深入

> 给代码维护者 / 架构师。讲清楚 AI 模块**为什么这么设计**,而不仅仅是**是什么**。

## 请求链路(AGENT 模式)

```
浏览器
  POST /api/ai/sessions/{id}/turns         (SSE)
     ↓
  AiTurnController (SseEmitter, 30min 超时, 虚拟线程跑 orchestrator)
     ↓
  AiOrchestrator.executeTurn(request, sink)
     ├── 按 SessionMode.from(session.getMode()) 分派
     │     CHAT  → TurnExecutor.execute()
     │     AGENT → AgentExecutor.execute()  ← 这里
     ↓
  AgentExecutor.execute(request, sink):

    1. MessagePersistence
       .insertUser(DONE)
       .insertAssistantPlaceholder(PENDING)
    2. StreamRegistry.register(messageId, sessionId)
         → CancellationHandle(streamId, Disposable 后绑)
    3. sink.emit(Meta{turnId, streamId, sessionId, ...})
    4. ContextBuilder.build(request, session, profile)
         → system prompt(base-role + dialect + active-script + selection
                          + pinned-tables + datasource + engine-visibility
                          + tool-guidelines + custom-override)
    5. ToolRegistry.allForWorkspace(wsId) + SkillToolProvider
         → List<AgentTool>
       每个包一层 RudderToolCallback:
         - PermissionGate.check
         - ToolApprovalRegistry.waitForApproval (写类)
         - AgentTool.execute
         - emit ToolCall / ToolResult
         - 落 tool_call / tool_result message
    6. ChatClientFactory.build(chatModel, ptk, ctk)
         默认 advisor:
           SimpleLoggerAdvisor
           RudderRagAdvisor       (检索 → 追加到 prompt)
           RedactionAdvisor       (PII 脱敏)
           UsageMetricsAdvisor    (累计 token)
    7. chatClient.prompt()
          .system(systemPrompt)
          .messages(history)
          .user(request.message)
          .options(ToolCallingChatOptions ...)
          .stream()
          .chatResponse()
          .doOnNext(resp → emit Token / Thinking; flusher.append)
          .doOnError(error::set)
          .doFinally(latch.countDown)
          .subscribe();   ← 拿到 Disposable
    8. handle.bindDisposable(disposable)
    9. latch.await();
    10. 结束处理:
        - DONE   → persistence.finishMessage(DONE) + emit Usage + Done
        - CANCEL → persistence.finishMessage(CANCELLED) + emit Cancelled
        - ERROR  → persistence.finishMessage(FAILED) + emit Error

     ↓
  SSE stream → 浏览器
```

## 模块边界

```
rudder-api/                        ← 控制器层
├── AiTurnController               POST /sessions/{id}/turns + /streams/{streamId}/cancel + /streams/{streamId}/tool-approve
├── AiSessionController            CRUD + GET /{id}/messages
├── AiDocumentController           知识库文档 CRUD + /upload + /reindex + /search
├── AiAdminController              MCP / tool-configs / dialects / metadata-sync / skills
├── AiUserController               feedback + pinned-tables + context-profiles
└── AiEvalController               eval cases + batches + runs
(平台级 provider 配置在 ConfigController:/api/config/ai-llm, /ai-embedding, /ai-vector, /metadata)

rudder-ai/                       ← AI 业务层(顶层模块)
└── io.github.zzih.rudder.service.ai
   ├── orchestrator/
   │     AgentExecutor / TurnExecutor    执行器
   │     ChatClientFactory               Spring AI ChatClient 组装(挂 4 个 advisor)
   │     ContextBuilder                  system prompt 拼装
   │     RudderToolCallback              工具 callback(生产)
   │     ToolApprovalRegistry            审批等待(基于 PubSubSignalRegistry)
   │     MessagePersistence              消息持久化
   │     StreamingRedactor / TokenFlusher / TurnEvent / TurnEventSink / ...
   │     advisor/ RudderRagAdvisor, RedactionAdvisor, UsageMetricsAdvisor
   ├── tool/
   │     ToolRegistry                    工具注册(扫描 AbstractBuiltinTool 子类)
   │     PermissionGate                  权限判定
   │     McpToolAdapter                  MCP 工具适配
   │     ToolOverviewService             admin tool 列表
   │     builtin/  20 个内置工具(详见 tools-mcp.md)
   ├── permission/ ToolConfigService     工具权限覆盖配置
   ├── mcp/ SpringAiMcpClient(Manager)   外部 MCP 接入(基于 spring-ai-starter-mcp-client)
   ├── skill/ SkillRegistry / SkillToolProvider / SkillAgentTool / SkillInvocationContext
   ├── dialect/ DialectService           方言加载(DB 覆盖 + classpath 默认)
   ├── context/ ContextProfileService / PinnedTableService
   ├── rag/
   │     DocumentIngestionService        入库 + 向量化
   │     DocumentRetrievalService        检索(语义 + FULLTEXT 降级)
   ├── knowledge/ MetadataSyncService / MetadataSyncScheduler / EngineCompatibility
   ├── feedback/ FeedbackService
   └── eval/
         EvalService / EvalExecutor / EvalVerifier / EvalToolCallback
         ExpectedSpec / OneshotResult / EvalMode

rudder-common/                    ← 通用
├── signal/ PubSubSignalRegistry  本地 handle + Redis pub/sub 基类
├── metrics/ MetricNames          统一 metric 命名
├── ratelimit/ RateLimitService
└── ...

rudder-service/rudder-service-server/  ← 平台级 server-only 服务
├── service/config/               LlmConfigService / EmbeddingConfigService /
│                                 VectorConfigService / MetadataConfigService / ...
├── service/redaction/ RedactionService  全局脱敏(平台级,不只给 AI)
└── service/stream/ StreamRegistry        流注册 + 取消

rudder-spi/                       ← SPI 契约 + provider 实现
├── rudder-llm/                   LLM provider SPI(模块名是 rudder-llm)
│     rudder-llm-api              契约(LlmClient / LlmClientFactory / LlmPluginManager)
│     rudder-llm-claude / openai / deepseek / ollama
├── rudder-vector/
│     rudder-vector-api
│     rudder-vector-qdrant / pgvector / local
├── rudder-embedding/
│     rudder-embedding-api
│     rudder-embedding-openai / zhipu
└── rudder-metadata/
      rudder-metadata-api
      rudder-metadata-jdbc / datahub / openmetadata

(脱敏未单独建 SPI 模块,实现在 rudder-common + service-server.redaction;
 MCP 也未建 SPI 模块,直接复用 spring-ai-starter-mcp-client。)
```

## 核心设计决策

### 1. 为什么 AGENT 和 CHAT 分两个执行器

早期混在一起,加个 `if (agentMode)` 分支。问题:
- AGENT 需要 `ToolCallingChatOptions`,CHAT 不要;分支代码滋生
- CHAT 不需要 `toolCallbacks`,传空列表浪费
- 未来 AGENT 可能要加工具循环策略、retry,CHAT 不需要

拆两个类后:类职责清晰,改一个不影响另一个。共享的部分(ChatClientFactory / ContextBuilder / MessagePersistence)抽出来复用。

### 2. 为什么 session mode 创建后不可变

UI 上切换 AGENT/CHAT 开关只影响**新 session**,不改已创建 session 的 mode。原因:

- AGENT 的 system prompt 包含工具清单,CHAT 没有;中途切 mode 上下文断裂
- AGENT 有 tool_call/tool_result 消息,CHAT 没有;中途切到 CHAT 继续对话,LLM 看到历史工具调用会困惑
- session 表的 `mode` 字段是**执行器分派的依据**(`SessionMode.from(session.getMode())`),改了会影响 turn 执行

所以 session 一生一个 mode,想切必须新建。UI 上用 `disabled + tooltip` 明示。

### 3. 为什么 Tool 审批走 future + 轮询混合

两种需求矛盾:
- **快响应**:用户点批准,agent 要尽快醒来(ms 级)
- **可取消**:agent 线程阻塞等批准时,turn 被 cancel 要能退出(不能卡死 5min)

方案:本地 `CompletableFuture.get(1s)` 超时轮询 + 每秒检查 `isCancelled()`。

- 同节点 approve → `future.complete()` 立即唤醒(μs)
- 跨节点 approve → `PubSubSignalRegistry.signal()` 经 Redis pub/sub 到持有 future 的节点再 complete(~10-50ms)
- cancel 检查 → 每秒一次,最多 1s 延迟

**不用纯 Redis 轮询**是因为同节点 case 占多数,响应延迟要压到 μs。

### 4. 为什么 StreamRegistry / ToolApprovalRegistry 共享 PubSubSignalRegistry 基类

两者本质相同:
- 本地存一个 JVM-bound 对象(`Disposable` / `CompletableFuture`)
- 跨节点时要"告诉持有对象的那个节点做操作"

抽出 `PubSubSignalRegistry<H>` 基类:
- 本地 Caffeine 存 handle(不可序列化)
- `signal(key, payload)` 先本地,miss 走 Redis pub/sub
- 订阅消息回到本地 handle,子类实现 `applyLocal(handle, payload)`

收益:
- 新增"等待用户反馈"类需求直接继承(不用再拼装 Caffeine+Redis)
- 测试统一(验基类的 pub/sub 路由逻辑,子类只测 applyLocal)

### 5. 为什么元数据缓存用 Redis 而不是 Caffeine

历史:曾经用 Caffeine + DB 事件表做跨节点失效。问题:
- 轮询延迟,改了 DDL 要等
- 多一张运维负担表
- 代码复杂(poller 线程 + event 插入 + 去重)

改 Redis 之后:
- 跨节点天然一致
- `invalidateCache` 一次 `SCAN + UNLINK` 全节点生效
- 删掉一张表 + 一套事件机制
- 写入多 1-3ms RTT(元数据不在超热路径,无感)

Caffeine 只留给**真不可序列化**的场景(`StreamRegistry.Disposable`, `ToolApprovalRegistry.CompletableFuture`)。

### 6. 为什么 eval 不复用 AgentExecutor

AgentExecutor 深度耦合 session / message / stream 持久化。eval 跑完不该污染这些表。

方案 A:给 AgentExecutor 加 `skipPersist` 参数 → 分支代码爆炸
方案 B(采用):独立 `EvalExecutor` 复用**关键无副作用组件**(`ChatClientFactory` / `ContextBuilder` / `LlmPluginManager` / `ToolRegistry`),工具层用 `EvalToolCallback`(不落库只采集调用轨迹,产物结构定义在 `OneshotResult`)

后者代码清晰,生产 agent 也不会因为 eval 需求被改乱。

### 7. 为什么 `t_r_ai_document` 按 docType × workspaceId 分 collection

Qdrant collection 名 `{workspaceId}_{docType}`(如 `0_SCHEMA`, `3_WIKI`)。

**好处**:
- 检索时**只查对的 collection**,速度快
- workspace 数据隔离天然(collection 独立)
- SCHEMA 平台共享时 `workspaceId=0` 所有 workspace 查同一个

**代价**:
- workspace 多时 collection 数 × docType 数 爆炸

当前规模下可接受。如果变大可考虑 Qdrant 单 collection + payload filter 方案。

## 数据模型

### 核心表(实际存在,见 schema.sql)

```
t_r_ai_session         ── 会话(持有 mode / model / title)
  └─ t_r_ai_message    ── 消息(role: USER / ASSISTANT / TOOL_CALL / TOOL_RESULT)
       └─ t_r_ai_feedback  ── 用户 👍/👎

t_r_ai_context_profile ── workspace / session 级 profile(覆盖默认 context 行为)
t_r_ai_pinned_table    ── pinned 表

t_r_ai_document        ── 知识库原文
  └─ t_r_ai_document_embedding  ── 向量映射(文档 chunk ↔ Qdrant pointId)

t_r_ai_metadata_sync_config ── 每数据源一条同步配置

t_r_ai_skill           ── skill(平台级 / workspace 级共表,通过 workspaceId 区分)
t_r_ai_dialect         ── dialect 覆盖(代码默认 + DB 覆盖)

t_r_ai_tool_config     ── 工具权限覆盖
t_r_ai_mcp_server      ── MCP server 注册

t_r_ai_config          ── LLM / EMBEDDING / VECTOR provider 配置(由 type 列鉴别)

t_r_ai_eval_case       ── 评测用例(mode / datasource / prompt / expectedJson / contextJson)
t_r_ai_eval_run        ── 评测执行结果(toolCalls / failReasons / tokens / latency)
```

### 关键约束 / 软删除

- 含 `deleted_at` 列的表走 soft-delete
- Qdrant 向量跟 `t_r_ai_document_embedding.qdrant_point_id` 对齐
- `t_r_ai_session.mode` 决定执行器分派,不可变
- `Datasource.name` 参与 DataHub URN / OpenMetadata FQN,不可变

## 一致性保障

### 三层防御(知识库 ↔ 向量库)

1. **入库时**:`tryIndex` 写 Qdrant 成功后 MySQL 失败,自动 `deleteByIds` 回滚 Qdrant(避免孤儿点)
2. **删除时**:`DocumentIngestionService.softDelete` 先清 Qdrant 再 soft-delete doc。元数据同步 / 配置删除 / 手工删除统一走这个出口
3. **兜底 sweep**:每次元数据同步结束扫 `embedding LEFT JOIN doc` 找孤儿,批量清 Qdrant + 删 embedding 行

### 跨库事务的软边界

无法完美保证(跨 MySQL + 远程 Qdrant):
- Qdrant 删成功 + MySQL commit 失败:doc 在 MySQL 活着但无向量 → 全文检索能用,语义跳过。`indexedAt=null` 是信号,admin 可重索引
- MySQL 直接 TRUNCATE:反查信息全丢,Qdrant 成孤儿池 → 只能 `reindexAll` + 手动清 Qdrant collection

### 审批幂等

`CompletableFuture.complete()` 本身幂等(第二次返回 false,不影响结果)。重复 HTTP 请求批准同一个 toolCallId 是安全的。

## 性能与限流

### 关键热路径

| 路径 | 频率 | 优化 |
|------|-----|------|
| `ContextBuilder.build` | 每 turn 1 次 | 静态 cache + 模板 cache |
| `ToolRegistry.allForWorkspace` | 每 turn 1 次 | 启动扫描,内存列表 |
| `DocumentRetrievalService.retrieve` | 每 turn 1 次(若开 RAG) | Redis 缓存 embedding 结果可选 |
| `MetadataClient.list*` | 工具调用 | Redis 缓存 5min |

### 限流

- `RateLimitService`(`rudder-common.ratelimit`):按 workspace / user 限流每分钟 turn 数
- Provider 侧:各家 API 有自己的 QPS 限制,超限 provider SDK 会返回 429

## 测试策略

| 层次 | 手段 |
|------|------|
| 契约 | arch-test 确保 SPI 包不依赖 Spring |
| 单元 | JUnit + Mockito,重点测 PermissionGate / Verifier / ExpectedSpec parse |
| 集成 | TestContainers 起 MySQL / Redis / Qdrant |
| 端到端 | eval 用例 + 真实 LLM(见 [评测](eval.md)) |

## 扩展点

### 加新 LLM provider

1. 新建 `rudder-spi/rudder-llm/rudder-llm-xxx` 模块
2. 实现 `LlmClientFactory` + `LlmClient`(继承 `SpringAiBackedLlmClient` 或自实现 `getChatModel()` 返回 Spring AI `ChatModel` 即享受所有 advisor / tool 机制)
3. `META-INF/services/io.github.zzih.rudder.llm.api.spi.LlmClientFactory` 注册
4. 加到 bundle 模块依赖,`LlmPluginManager` 通过 ServiceLoader 自动发现

### 加新工具

1. 继承 `AbstractBuiltinTool`(实现 `AgentTool`)
2. 实现 `name()` / `description()` / `inputSchema()` / `execute(input, ctx)`
3. `@Component` 注解,启动自动被 `ToolRegistry` 扫描
4. 写 eval case 验证工具被 agent 正确调用

### 加新 Advisor

1. 实现 `BaseAdvisor` / `CallAroundAdvisor` / `StreamAroundAdvisor`
2. 加到 `ChatClientFactory.build` 的 advisor 链里
3. 注意顺序(`@Order`):RAG 在 Redaction 前等

## 相关文档

- [Provider 配置](providers.md)
- [评测](eval.md)
- [运维](operations.md)
