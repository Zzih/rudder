# 架构总览

> 给架构师 / 维护者：模块边界、依赖方向、跨节点协调机制、扩展点。配置 / 部署细节请看 [配置参考](configuration.md) / [部署指南](deployment.md)。

## 1. 项目定位

Rudder 是**统一批流大数据 IDE 平台**：开发、调度、执行、监控批 / 流任务的一站式工具。前端做工作流编排和脚本编辑，后端把工作流发布到 DolphinScheduler / 自建 Worker 执行，配套数据源管理、元数据查询、脱敏、版本控制、AI Copilot 等。

技术栈：Java 21 + Spring Boot 4.0.5 + MyBatis-Plus 3.5.15 + Spring AI 2.0.0-M6 + Maven 多模块；前端 Vue 3 + TypeScript + Vite 6。

## 2. 部署形态

```
┌─────────────────────┐         ┌────────────────────────┐
│ rudder-api  (1+)    │←─Netty─→│ rudder-execution (1+)  │
│ HTTP 5680 / RPC 5690│   RPC   │ HTTP 5681 / RPC 5691   │
│ REST + SSE + 静态资源│         │ TaskPipeline 执行任务  │
└──────────┬──────────┘         └──────────┬─────────────┘
           │                                │
           └────────┬─── shared infra ──────┘
                    ↓
   ┌────────────────────────────────────────────────┐
   │ MySQL（持久化主库 + t_r_service_registry）     │
   │ Redis（流取消 / 限流 / 预算 / GlobalCache 广播）│
   │ FileStorage（local / hdfs / oss / s3）         │
   │ External: DolphinScheduler / DataHub / LLM ... │
   └────────────────────────────────────────────────┘
```

- `rudder-api` 是 HTTP 入口，负责 REST、SSE、AI 编排、控制流节点编排
- `rudder-execution` 是 Worker，跑实际任务（SQL / Spark / Flink / Shell / Python / HTTP / SeaTunnel）
- 双进程都可水平扩展；自研 Netty RPC 互通（auth-secret HMAC + 5min 防重放）
- MySQL + Redis 是平台**强依赖**，不提供 fallback / memory-only 模式

## 3. 模块全景

```
rudder/
├── rudder-common            纯工具 / POJO / 常量。无 Spring bean、无基础设施
├── rudder-dao               MyBatis-Plus DAO + 实体 + 枚举 + Mapper XML
├── rudder-rpc               自研 Netty RPC 框架 + 服务契约接口（IXxxService）
├── rudder-datasource        数据源连接池 + JDBC 客户端 + Calcite SQL AST 解析
├── rudder-spi/              所有可扩展的 SPI（每个 SPI 一个子目录）
│   ├── rudder-spi-api       SPI 通用基础设施（AbstractPluginRegistry / ProviderContext / ...）
│   └── rudder-{xxx}/        每个 SPI 一个目录，下含 -api + 多个 -{provider}
├── rudder-service/          业务逻辑层
│   ├── rudder-service-shared        api 与 execution 共用业务（缓存 / 协调 / 脱敏 / 配置 / ...）
│   └── rudder-service-server        仅 api 端业务（工作流编排 / workspace / publish / 调度）
├── rudder-ai                AI Copilot 业务（顶级模块；orchestrator / tool / rag / mcp / skill / ...）
├── rudder-bundles/          provider 装配清单（决定哪些 plugin 进 dist）
│   ├── rudder-bundle-api             api 端要带的 SPI provider 集合
│   └── rudder-bundle-execution       worker 端要带的 SPI provider 集合
├── rudder-api               HTTP API 服务（启动应用）
├── rudder-execution         Worker 服务（启动应用）
├── rudder-arch-tests        ArchUnit 架构约束测试
├── rudder-ui                Vue 前端
└── rudder-dist              tarball 打包（自带 bin 启动脚本）
```

## 4. 模块依赖图

```
                       rudder-common
                            ↑
                ┌───────────┼───────────┐
                ↑           ↑           ↑
            rudder-dao   rudder-rpc   rudder-spi-api
                            ↑           ↑
                            └─────┬─────┘
                                  ↑
                       rudder-spi/{xxx}-api          ← SPI 接口
                                  ↑
                       rudder-spi/{xxx}-{provider}   ← provider 实现
                                  ↑
       ┌──────────────────────────┼──────────────────────────┐
       ↑                          ↑                          ↑
  rudder-bundle-api               ↑               rudder-bundle-execution
       ↑                          ↑                          ↑
       └────→  rudder-datasource  ↑     rudder-service-shared ←──┘
                                  ↑                ↑
                                  ↑                ├─→ rudder-ai
                                  ↑                ↑       ↑
                                  ↑       rudder-service-server
                                  ↑                ↑
                              rudder-api    rudder-execution
                                  ↑                ↑
                                  └─── rudder-dist ┘
```

ArchUnit `SpiArchitectureTest` 守护硬约束：
- SPI `-api` 不依赖 `-{provider}`
- 任何 SPI 模块不依赖 `rudder-service` / `rudder-ai`
- provider 模块互不依赖
- `rudder-rpc` 只放契约，业务实现归 server / execution

## 5. SPI 扩展模型

每个 SPI = 一个能力点，有"多 provider 切换"诉求才设。当前 11 个 SPI：

| SPI | 作用 | provider |
|:---|:---|:---|
| task | 任务类型 | mysql / postgres / hive / starrocks / doris / clickhouse / trino / spark / flink / python / shell / http / seatunnel |
| runtime | 执行运行时 | local / aliyun / aws |
| metadata | 元数据来源 | jdbc / datahub / openmetadata |
| llm | 大模型 | claude / openai / deepseek / ollama |
| embedding | 向量化模型 | openai / zhipu |
| vector | 向量库 | local / pgvector / qdrant |
| file | 文件存储 | local / hdfs / oss / s3 |
| approval | 审批渠道 | local / lark / kissflow |
| version | 版本存储 | local / git |
| notification | 通知渠道 | lark / dingtalk / slack |
| result | 查询结果格式 | json / csv / parquet / orc / avro |

约定：

- `rudder-{xxx}-api` 接口契约 + `XxxPluginManager`（纯工厂，只暴露 `create(provider, config)`）
- `rudder-{xxx}-{provider}` 实现，类带 `@AutoService(XxxFactory.class)` 自动注册
- `rudder-bundles/rudder-bundle-{api|execution}` 决定 dist 里带哪几个 provider
- provider 包结构：`io.github.zzih.rudder.{xxx}.{provider}.*` 平铺，禁止嵌套 `service / dto / `

详细写法见 [SPI 开发指南](spi-guide.md)。

## 6. service-shared 与跨节点协调

`rudder-service-shared` 顶级目录：

```
io.github.zzih.rudder.service/
├── coordination/             跨节点协调基础设施
│   ├── BroadcastEvent / RedisBroadcaster / RedisNaming
│   ├── NodeIdProvider                     节点身份（node-id 标签 + 默认 hostname-pid）
│   ├── cache/  GlobalCacheService         项目唯一全局缓存入口
│   ├── signal/ PubSubSignalRegistry       通用 pub/sub 信号（StreamRegistry / ToolApprovalRegistry 复用）
│   └── token/                             分布式 token / 限流 / 预算扣减
├── config/                   AbstractConfigService + 各 SPI 的 ConfigService
├── redaction/                LocalRedactionService + RedactionRuleCache + 日志桥接
├── version/                  DelegatingVersionStore（VersionStore 唯一可注入 bean，热切换）
├── notification/             NotificationService
├── mcp/                      MCP 协议 client（SpringAiMcpClientManager 在 rudder-ai 里）
├── stream/                   StreamRegistry / CancellationHandle（AI turn 流控）
├── metadata/                 MetadataClient cache + tag 解析
├── workspace/                AuthService（JWT 签发 / 校验）
└── workflow/                 共享 workflow DTO（指令 / 实例查询接口）
```

### `GlobalCacheService` 双模缓存（核心）

由 `GlobalCacheKey` 上的 `CacheSpec` 声明，业务方无感选择：

| 模式 | 行为 | 适用 |
|:---|:---|:---|
| **LOCAL** | L1 Caffeine + Redis 仅广播失效（Redis 不存数据） | 小配置（LLM / FILE / DIALECT 等 12 项） |
| **SHARED** | L1 Caffeine + L2 Redis 直存（带 TTL） + Redis SETNX 单 flight + 广播失效 | 大数据集 / 跨节点共享 / 拉源贵的场景（metadata 数据） |

写顺序（SHARED）：先 L2 → 再 L1 → 再广播，保证其他节点收到广播后下次查能拿到新值。L2 强制带 TTL 兜底广播丢失。

资源生命周期：Caffeine `removalListener` 自动 `close()` 实现 `AutoCloseable` 的 cache value（VersionStore / MetadataClient / 等）；`@PreDestroy` 在优雅停机时清空所有 cache，触发批量 close。

### XxxConfigService 模板

继承 `AbstractConfigService<E, T>`：

- `T active()` 返 nullable 当前实例
- `T required()` 抛 `BizException(ConfigErrorCode.X_NOT_CONFIGURED)`
- `void save(E c)` DB upsert + 缓存广播失效
- `HealthStatus health()` `instance.healthCheck()`

热切换：UI 改配置 → service `save` → `cache.invalidate(GlobalCacheKey.X)` → 全节点 invalidateAll → `removalListener` close 旧实例 → 下次 `active()` 时 loader 拿新实例。下一个调用方立即看到新 client，不重启进程。

## 7. service-server 与 ai 业务

```
rudder-service-server/.../service/
├── workflow/    DAG 编排：DagParser / WorkflowInstanceRunner / CompletionEventRouter / VarPoolManager
│   ├── controlflow/  CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT 本地编排（不进 SPI）
│   ├── executor/     WorkflowExecutor / ResumeStateReconciler / WorkflowOrphanReaper
│   ├── ApprovalService / WorkflowPublishService（RudderDolphinPublisher 见 rudder-spi/rudder-publish/rudder-publish-rudder-dolphin）
│   └── WorkflowScheduleService（Quartz）
├── workspace/   用户 / Workspace / 项目 / 审计 / SSO（OIDC + LDAP）
├── config/      仅 Server 端的 *ConfigService（含 LlmConfigService / EmbeddingConfigService / ...）
├── redaction/   admin 端：MetadataColumnTagResolver + RedactionAdminService
└── metadata/    metadata sync orchestration

rudder-ai/.../service/ai/
├── orchestrator/  AgentExecutor / TurnExecutor / ChatClientFactory / TurnEventSink / MessagePersistence
│                  advisor 链：RedactionAdvisor / RudderRagAdvisor / UsageMetricsAdvisor
├── tool/          ToolRegistry / RudderToolCallback / PermissionGate
│   └── builtin/   20 个内置工具（list / describe / sample / run-sql / script CRUD / ...）
├── skill/         SkillRegistry / SkillAgentTool（一个 skill = 一个嵌套 agent）
├── rag/           DocumentIngestionService + DocumentRetrievalService
├── knowledge/     MetadataSyncService / MetadataSyncScheduler（DDL → ai_document）
├── dialect/       SQL 方言识别（按数据源类型 → prompt slot）
├── eval/          EvalService / EvalExecutor / EvalVerifier / EvalToolCallback
├── mcp/           SpringAiMcpClient + SpringAiMcpClientManager（基于 spring-ai-starter-mcp-client）
├── feedback/      FeedbackService（点赞 / 吐槽）
├── permission/    ToolConfigService（按 workspace 启停工具）
└── context/       ContextProfileService + PinnedTableService
```

LLM / Embedding / Vector / Metadata 都通过 ConfigService 拿 active provider，admin 后台配置一键热切换。

## 8. 任务执行模型

### 8.1 控制流

工作流支持 4 种控制流节点：`CONDITION` / `SWITCH` / `SUB_WORKFLOW` / `DEPENDENT`，由 Server 端 `service.workflow.controlflow` 直接编排，**不下发到 Execution**。字段语义对齐 DolphinScheduler，便于 Rudder 工作流发布到 DS 后无缝运行。

### 8.2 任务运行链路

```
HTTP POST /api/workflow-instances（rudder-api）
   ↓ WorkflowInstanceService 创建实例
   ↓ WorkflowInstanceRunner（线程池）按 DAG 推进
   ├─ 任务节点 → TaskInstanceFactory 落库 + RPC dispatch
   └─ 控制流节点 → ControlFlowTaskFactory 本地编排

任务派发链路：
   Server.WorkflowInstanceRunner
      └→ rpcClient.proxy(ITaskExecutionService).dispatch(req)
            ↓ Netty
   Execution.TaskExecutionServiceImpl.dispatch
      └→ TaskPipeline 异步执行
         ├─ ResourceResolver（FileStorage 下载 jar / 资源）
         ├─ RuntimeInjector（按 TaskType 选 Runtime）
         ├─ JdbcResourceInjector（解密数据源凭证）
         ├─ task.run()（HiveSql / SparkJar / Python / ...）
         ├─ QueryResultCollector（脱敏 + 格式化 + 落 FileStorage）
         └─ rpcClient.proxy(ITaskCallbackService).onTaskFinished(...)
```

详见 [工作流](workflow.md) / [任务类型](task-types.md) / [Runtime 适配器](runtime-adapters.md)。

### 8.3 RPC 框架

自研 Netty 4 RPC，详见 [RPC 协议](rpc.md)：

- 协议帧：`MAGIC=0x5244` + VERSION + HEADER_LEN + BODY_LEN + JSON header + JSON body
- 鉴权：HMAC-SHA256(`secret`, `methodId|opaque|timestamp`) + 5min 防重放
- 契约：`I*Service` 接口标 `@RpcService`，方法标 `@RpcMethod`
- 实现：`@Component` 自动被 `RpcAutoConfiguration` 扫到注册

服务实现位置：

- Execution 实现 → `rudder-execution/.../execution/rpc/{TaskExecutionServiceImpl, LogServiceImpl, ResultServiceImpl}`
- Server 实现 → `rudder-api/.../api/rpc/{TaskCallbackServiceImpl}`

`rudder-rpc` 模块只放契约，**禁止业务实现**。

### 8.4 服务注册

```sql
t_r_service_registry (
  id, host, http_port, rpc_port, type,        -- type = SERVER / EXECUTION
  status,                                     -- ONLINE / OFFLINE
  last_heartbeat_at,
  node_id,                                    -- 来自 RUDDER_NODE_ID
  created_at, updated_at
)
```

- 启动时 upsert，10s 心跳 update `last_heartbeat_at`
- Server 选 Execution 时 `WHERE type='EXECUTION' AND status='ONLINE'`
- 30s 未心跳 → 标 OFFLINE（OrphanReaper）

## 9. AI 模块亮点

详见 [AI 模块](ai/README.md)：

- **CHAT / AGENT** 两种执行器分派：`session.mode` 决定走 `TurnExecutor`（无工具）或 `AgentExecutor`（带工具循环）
- **Advisor 链路**：`SimpleLoggerAdvisor → RudderRagAdvisor（before）→ RedactionAdvisor（after）→ UsageMetricsAdvisor（after）`
- **工具体系**：20 个内置工具 + 配 workspace 可见的 MCP 工具，统一包 `RudderToolCallback`
  - `PermissionGate`（角色 + readOnly 校验）
  - `ToolApprovalRegistry`（写类工具等待用户审批 → CompletableFuture + Redis pub/sub）
  - 落 `tool_call` / `tool_result` 消息
- **流取消**：`StreamRegistry` 本地 Caffeine 存 `Disposable` + Redis pub/sub 广播取消信号
- **RAG**：文档 → Embedding → Vector Store；Qdrant collection 按 `{workspaceId}_{docType}` 分

## 10. 模块职责硬约定

| 模块 | 必须 | 禁止 |
|:---|:---|:---|
| `rudder-common` | 纯工具 / POJO | Spring bean、Redis、Caffeine、跨节点协调 |
| `rudder-dao` | MyBatis 实体 + DAO 接口 + Mapper XML | 业务逻辑 |
| `rudder-rpc` | 通信框架 + 契约 | 业务实现（impl 落到 server / execution） |
| `rudder-spi-api` | 接口契约 + PluginManager（纯工厂） | active 状态、缓存、Redis pub/sub |
| `rudder-spi/{xxx}-{provider}` | factory 实现 + 内部细节 | 跨模块业务引用 |
| `rudder-service-shared` | api / execution 共用业务 | 单端逻辑 |
| `rudder-service-server` | 仅 api 端业务（编排 / publish / workspace / admin） | execution worker 用的代码 |
| `rudder-ai` | AI 业务 | execution worker 用的代码 |

## 11. 关键约定

1. **包命名**：每个模块顶级包 `io.github.zzih.rudder.{module}.*`；`rudder-service-*` 与 `rudder-ai` 都在 `service.*` 下，靠子包区分（`service.workflow / service.ai / ...`）
2. **配置缓存**：所有跨节点配置一致性走 `GlobalCacheService`，禁止业务侧自建 Redis JSON 或 volatile 字段
3. **后端优先**：查询 / 搜索 / 过滤 / 聚合一律走后端接口；前端不自己拉全量过滤、不自己分页
4. **DTO 边界归属**：API request/response → `rudder-api`；DAO entity → `rudder-dao`；RPC 契约 DTO → `rudder-common.execution`；跨模块共享纯 POJO → `rudder-common.model`
5. **类型 enum**：状态 / 类型字段走 enum（`RuntimeType` / `TaskType` / `RoleType` / `RedactionExecutorType` 等），禁止裸字符串
6. **MyBatis-Plus**：基础 CRUD 用 `BaseMapper` + 注解；复杂查询走 XML
7. **soft-delete**：仅 `t_r_ai_session` / `t_r_ai_document` 用 `deleted_at`；其它表硬删

## 12. 错误码体系

`rudder-common/result/ErrorCode` 接口 + 各域枚举：

- `ConfigErrorCode`（6001-6010）SPI 配置未就绪 / 失效
- `LlmErrorCode` / `WorkflowErrorCode` / `WorkspaceErrorCode` / `DatasourceErrorCode` / `ScriptErrorCode` 各域内
- 控制器返回 `Result.fail(ErrorCode)`，全局异常处理器统一序列化

## 13. 部署流程

```
mvn package  →  rudder-dist/target/rudder-{version}-SNAPSHOT.tar.gz
              ↓
       解压 → bin/rudder-daemon.sh start all
       配 .env / application.yml 指 MySQL / Redis
       JVM 起 → schema.sql + data.sql 自动初始化 → 注册到 t_r_service_registry
```

详见 [部署指南](deployment.md)。

## 相关文档

- [快速开始](quickstart.md) — 10 分钟跑通
- [配置参考](configuration.md) — 全部 env / yml 字段
- [SPI 开发指南](spi-guide.md) — 写新 provider
- [RPC 协议](rpc.md) — 通信细节
- [工作流](workflow.md) / [任务类型](task-types.md) / [Runtime 适配器](runtime-adapters.md)
- [AI 模块](ai/README.md)
- [数据库 schema](database-schema.md)
