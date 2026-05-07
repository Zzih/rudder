# Rudder

**AI 流批一体数据平台**

*让每一位数据人都能在数据的海洋中自由掌舵。*

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-blueviolet.svg)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)

中文 | [English](README.en.md)

---

Rudder 是一个 **AI 优先**的流批一体数据平台。AI Agent 与 MCP 协议被设计为平台一等公民,横向贯通 IDE、元数据、数据源、权限与审计;批 + 流数据开发能力(在线编辑、DAG 工作流、多引擎执行)通过 11 类 SPI 插件化扩展,支持 Hive / Spark / Flink / Trino / StarRocks / Doris / SeaTunnel 等主流引擎,以及 AWS / 阿里云 / 自建集群 / Serverless 多种 Runtime 形态。

**Rudder 的差异化**:别家在数据平台之上"装一个 AI 助手",Rudder 是把 AI Agent + MCP 双向通道作为平台第一性原理设计 —— 工作空间能力(元数据、SQL 执行、脚本、工作流)通过 MCP 反向暴露给 Cursor / Claude Desktop 等任意外部 AI IDE,配合 PAT + Capability + 二级审批,做到**"工作空间能力跟着用户走"**。

## 一图速览

```
┌──────────────────── 用户入口 ─────────────────────┐
│                                                                                │
│   Web IDE (Vue 3)         Cursor / Claude Desktop      curl / SDK              │
│   JWT / SSO 登录          MCP 协议 + PAT 鉴权          REST API                │
│                                                                                │
└─────────────┬──────────────────┬───────────────────────┬──────────────────────┘
              │                  │                       │
              ▼                  ▼                       ▼
┌──────────────────────────── Server (rudder-api) ───────────────────────────────┐
│                                                                                │
│  REST + SSE          MCP Server (Spring AI)         AI Orchestrator             │
│  权限/审计/脱敏      19 Capability + PAT scope       Tool Calling Loop           │
│  发布审批 (Lark/...) 二级审批 (WRITE / HIGH)         3 Advisor 链路              │
│                                                                                │
│  Workflow DAG + Cron + 4 控制流节点 (CONDITION/SWITCH/SUB_WORKFLOW/DEPENDENT)   │
│                                                                                │
└─────────────┬──────────────────────────────────────────────────┬───────────────┘
              │ RPC (Netty + auth-secret)                        │
              ▼                                                  │
┌─────────── Execution (rudder-execution) ───────────┐           │
│  TaskPipeline:                                     │           │
│  ResourceResolver → RuntimeInjector →              │  MySQL / Redis  ←─────────
│  JdbcResourceInjector → ResultCollector(redact)    │
└────────────────────────────────────────────────────┘
```

## 核心特性

### AI Agent

- **Tool Calling Agent** — Spring AI `internalToolExecutionEnabled=true` 模式,直接执行 22 个内置 Tool(脚本 CRUD / SQL readonly / 元数据浏览 / 表采样 / 执行日志 / RAG 检索 等)
- **多轮会话与流式** — Session/Turn 模型,SSE 流式输出,thinking 链路独立 channel
- **跨节点流取消** — Redis pub/sub 广播 + Reactor `Disposable`,不依赖 thread interrupt;前端 Esc 即停
- **Advisor 链路** — `RedactionAdvisor`(出入口脱敏)+ `RudderRagAdvisor`(自动 RAG)+ `UsageMetricsAdvisor`(token 计量)
- **写类工具二次确认** — Agent 调写类 Tool 前 emit `ToolApproval` 事件,前端弹确认框,用户点准才放行
- **Skill + Pinned Tables + Context Profile** — workspace 级 Skill 自动暴露成 Tool;高频用表自动注入 system prompt;rag 开关 / 引擎白名单 / 模型 / token 预算按工作空间配置
- **预算与限流** — Provider 路由、模型级 rate limit、Token 预算扣减(Redis 原子)
- **多模型** — Claude / OpenAI / DeepSeek / Ollama,按场景路由
- **反馈与离线评测** — like/dislike 闭环 + 离线 eval case/run,独立链路不落库

### MCP — 既是客户端也是服务端

Rudder 把 MCP(Model Context Protocol)做成了**双向公民**:**作为客户端**接入任意外部 MCP Server,把它们的工具合并到 Rudder Agent 的 tool set;**作为服务端**暴露 MCP 端点,让 Cursor / Claude Desktop / VS Code MCP Inspector 等外部 AI 工具直接调用 Rudder 的工作空间能力。

**MCP 工具集**(8 个域):

| 域 | 示例工具 |
|:---|:---|
| **workspace / project** | view / browse / author |
| **metadata** | browse / list catalogs / list tables / describe table |
| **datasource** | view / test / manage(SUPER_ADMIN 限定) |
| **script** | browse / search / get / create / update / delete / move / rename / dispatch |
| **execution** | view_status / view_result / run / cancel |
| **workflow** | browse / author / run / publish / 实例查询 / 调度管理 |
| **approval** | view / act |

**安全模型 —— Capability + Token + 审批 三层**:PAT(`rdr_pat_xxx`,bcrypt cost=10 校验)持有 19 个 capability 子集,通过 `ScopeChecker` 双闸门(Capability scope + 用户当前 workspace RBAC role)校验。READ 类创建即发放;WRITE 类进入 `PENDING_APPROVAL` 由 workspace owner 批准激活;HIGH 敏感度(`execution.run` / `datasource.manage` / `workflow.publish`)走 owner → SUPER_ADMIN 二级审批。每次调用经 `McpToolGuardAspect`:Redis 限流(默认 60 req/min)→ 审计落库(`t_r_audit_log` 记录谁/token/tool/参数/耗时/成败)→ 执行。`McpRoleChangeListener` 在用户角色 demote 时自动撤销不再持有的 capability grant。

### 知识库 / RAG

- **多种向量库** — pgvector / Qdrant(语义检索)/ 本地 MySQL FULLTEXT(零部署关键词召回)
- **平台级元数据同步** — 跨工作空间共享 SCHEMA / WIKI / METRIC_DEF / RUNBOOK / SCRIPT 5 类文档,定时拉取写入向量库
- **Engine 兼容性** — RAG 检索按 TaskType 自动过滤兼容引擎(查询 Hive 不会召回 StarRocks 文档)
- **MCP 检索工具** — `search_documents` 暴露给 Agent 自主决定是否检索,也可走 RudderRagAdvisor 自动模式

### 数据开发(批 + 流)

- **在线 IDE** — Monaco Editor 编辑 SQL / Python / Shell,即时执行并查看结果
- **工作流编排** — AntV X6 可视化 DAG 编辑器 + 4 类控制流节点(CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT),内置 Cron 调度
- **多引擎** — Hive / StarRocks / Doris / ClickHouse / Trino / Spark / Flink / MySQL / PostgreSQL / Python / Shell / SeaTunnel / HTTP(13 类 TaskChannel)
- **流批一体** — Flink SQL / Flink JAR 原生 STREAMING 模式,与批任务在同一 IDE 统一管理
- **Runtime 插件化** — 已适配本地集群 / 阿里云(Ververica + EMR Serverless)/ AWS(EMR Serverless + Managed Flink)
- **多格式导出** — JSON / CSV / Parquet / ORC / Avro

### 平台治理

- **弹性架构** — Server / Execution 分离,自研 Netty RPC,多实例水平扩展,不依赖 ZooKeeper
- **元数据治理** — DataHub / OpenMetadata GraphQL + JDBC 兜底,IDE 内置表结构浏览
- **权限体系 + SSO** — RBAC 4 级角色 + 数据源 Workspace 级授权;OAuth2 / OIDC / LDAP 三类 SSO
- **发布审批** — Lark / Slack / KissFlow 三类渠道(SPI 可扩展);项目发布 / MCP token 共用同一审批引擎
- **版本管理 + 生产调度** — 脚本 / 工作流快照 + Diff,MySQL / Git 双 backend;内置 Cron 可对接 DolphinScheduler
- **数据脱敏** — 全局脱敏管线,SQL 结果 / 数据源凭证 / 日志输出三个出口统一走规则
- **审计 + 多文件存储 + 国际化** — 全操作审计;LOCAL / HDFS / OSS / S3 文件存储;后端 5 类 i18n bundle + 前端中英双语

## 技术栈

| 层 | 技术 |
|:---|:-----|
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis-Plus 3.5.15、Spring AI 2.0.0-M4 |
| 前端 | Vue 3、TypeScript、Vite 6、Element Plus、Monaco Editor、AntV X6、Pinia |
| 通信 | 自研 Netty RPC 框架(含 auth-secret 校验) |
| 持久化 | MySQL 8.x(主库 + 服务注册)、Redis(流取消 pub/sub、限流、预算、缓存) |
| 大数据 | Hive、StarRocks、Doris、ClickHouse、Trino、Spark、Flink、SeaTunnel、HDFS |
| 云平台 | 阿里云(Ververica Flink + EMR Serverless Spark)、AWS(EMR Serverless + Managed Flink) |
| AI | Claude / OpenAI / DeepSeek / Ollama,OpenAI / 智谱 Embedding,pgvector / Qdrant / 本地 FULLTEXT |
| MCP | 平台自身 MCP Server(Spring AI MCP)+ PAT 鉴权;同时作为 MCP Client 接外部 server |
| 元数据 | DataHub、OpenMetadata(GraphQL)+ JDBC 兜底 |
| 审批/通知 | 飞书 OAPI、Slack API、钉钉、KissFlow |

> **强依赖:MySQL 8.x + Redis**。两者必须存在且可用,平台不提供任何降级 / fallback / memory-only 模式(流取消广播、限流、预算扣减、PAT 校验缓存强依赖 Redis)。

## 架构

```
Server (rudder-api)                          Execution (rudder-execution)
  HTTP 5680 / RPC 5690                         HTTP 5681 / RPC 5691
├ REST API + 前端静态资源 (file:ui/)         ├ RPC 接收任务执行请求
├ JWT / SSO / RBAC                            ├ TaskPipeline 执行管线
├ MCP Server endpoint + PAT 鉴权              │  ├ ResourceResolver(资源依赖解析)
├ AI Orchestrator + Tool Calling              │  ├ RuntimeInjector(运行时注入)
├ 创建 TaskInstance                            │  ├ JdbcResourceInjector(JDBC 注入)
├ Workflow DAG 调度 + 控制流节点               │  └ ResultCollector(结果收集 + 脱敏)
│   (CONDITION / SWITCH /                     ├ 日志写本地文件 + DB
│    SUB_WORKFLOW / DEPENDENT)                ├ 服务注册 (type=EXECUTION)
├ RPC 派发任务到 Execution                     └ 心跳 10s
├ 日志/结果查询转发
├ 服务注册 (type=SERVER)
└ 心跳 10s

           ↕ MySQL(持久化主库 / 服务注册 / 工作流 / AI 会话 / 脱敏规则 / MCP token / 审计 等)
           ↕ Redis(流取消 pub/sub / 限流 / 预算 / TokenViewCache / 全局缓存)
```

- 多 Server / 多 Execution 实例水平扩展
- 服务注册到 MySQL `t_r_service_registry`,10s 心跳、30s 超时自动 OFFLINE
- RPC 自研 Netty 框架,含 auth-secret 校验(≥32 字节,两端必须一致)

## 模块结构

```
rudder/
├── rudder-common                 通用:Result、异常、ErrorCode 枚举、I18n、审计、Stream 注册、工具类
├── rudder-dao                    Entity / Mapper / DAO / 枚举(schema.sql:43+ 张 t_r_* 表)
├── rudder-rpc                    自研 RPC 框架(Netty + auth-secret 校验)
├── rudder-datasource             数据源管理 + AES 凭证加密 + 连接池
├── rudder-spi/                   所有可插拔扩展点
├── rudder-service/
│   ├── rudder-service-shared         API 与 Execution 共享(script / task / workflow / metadata / approval / version / notification / i18n / redaction / stream registry 等)
│   ├── rudder-service-server         仅 Server 使用(publish、调度、SPI 配置、WorkflowInstanceRunner、ControlFlow Executor)
│   └── rudder-mcp                    MCP Server: PAT 鉴权 / Capability / Tool 守卫 / 审计 / 角色联动 / 客户端接入指南
├── rudder-ai                     AI 能力聚合
│   ├── orchestrator                  AiOrchestrator + AgentExecutor (AGENT) + TurnExecutor (CHAT)
│   ├── orchestrator/advisor          RedactionAdvisor / RudderRagAdvisor / UsageMetricsAdvisor
│   ├── orchestrator/tool             RudderToolCallback / ToolApprovalRegistry
│   ├── orchestrator/turn             TurnEvent 事件流(Token/Thinking/ToolCall/ToolApproval/Error/Meta)
│   ├── tool + tool/builtin           ToolRegistry + 22 个内置 Tool
│   ├── mcp                           McpClientManager(作为 MCP Client 连外部 server)
│   ├── skill                         SkillRegistry / SkillToolProvider / SkillAgentTool
│   ├── rag                           DocumentIngestionService / DocumentRetrievalService
│   ├── knowledge                     EngineCompatibility / MetadataSyncService(平台级共享文档)
│   ├── feedback + eval               FeedbackService + EvalService(离线评测,独立链路不落库)
│   ├── context + permission          ContextProfileService + PinnedTableService + PermissionGate
│   └── dialect                       DialectService(DB 覆盖 + classpath 默认)
├── rudder-bundles/               SPI provider 依赖聚合(api / execution 两份)
├── rudder-api                    Server 入口(REST Controller + JWT,HTTP 5680 / RPC 5690)
├── rudder-execution              Worker 入口(TaskPipeline + 日志管理,HTTP 5681 / RPC 5691)
├── rudder-arch-tests             ArchUnit 守护 SPI 分层规则
├── rudder-ui                     Vue 3 前端(Vite 6 + Element Plus + Monaco + X6)
└── rudder-dist                   打包 tarball + Server / Execution Dockerfile
```

## SPI 插件体系

Rudder 采用统一的 `AbstractPluginRegistry<K, F>` 管理可插拔扩展,所有 SPI 模块严格遵循 `-api` / `-local` / `-<provider>` 命名约定,新增引擎或平台只需实现对应工厂接口 + 一个注解。

| SPI 组 | 模块 | provider | 说明 |
|:---|:---|:---|:---|
| **Task** | `rudder-task` | mysql / postgres / hive / starrocks / doris / clickhouse / trino / spark / flink / python / shell / seatunnel / http | 任务执行引擎(13 个 + api) |
| **Runtime** | `rudder-runtime` | local / aliyun / aws | 执行环境抽象 |
| **Metadata** | `rudder-metadata` | jdbc / datahub / openmetadata | 元数据提供者 |
| **LLM** | `rudder-llm` | claude / openai / deepseek / ollama | 大语言模型 |
| **Embedding** | `rudder-embedding` | openai / zhipu | 文本向量化 |
| **Vector** | `rudder-vector` | local / pgvector / qdrant | 向量存储 |
| **File** | `rudder-file` | local / hdfs / oss / s3 | 文件存储 |
| **Approval** | `rudder-approval` | local / lark / kissflow | 发布审批 |
| **Notification** | `rudder-notification` | lark / dingtalk / slack | 通知渠道 |
| **Version** | `rudder-version` | local / git | 版本持久化 |
| **Result** | `rudder-result` | json / csv / parquet / orc / avro | 结果序列化(Java SPI + `@AutoService`) |

> 控制流节点(CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT)不是独立 TaskChannel SPI,而是在 Server 端 `service.workflow.controlflow` 由 WorkflowInstanceRunner 直接编排执行,不派发到 Execution。

每个 provider 都自带:
- 中英双语 Markdown 接入指南(`spi-guide/<family>-<provider>.{zh,en}.md`)— 装在 jar 里随 provider 走
- 前端表单参数声明 + i18n key(`PluginParamDefinition`)— Web UI 自动渲染
- `validate(config)` + `testConnection(ctx, config)` 钩子 — 保存前校验、保存后测试

## 任务类型

| 类型 | TaskType | 数据源类型 | 执行模式 |
|:-----|:---------|:----------|:---------|
| SQL | HIVE_SQL / STARROCKS_SQL / MYSQL / DORIS_SQL / POSTGRES_SQL / CLICKHOUSE_SQL / TRINO_SQL / SPARK_SQL | HIVE / STARROCKS / MYSQL / DORIS / POSTGRES / CLICKHOUSE / TRINO / SPARK | BATCH |
| 流批 SQL | FLINK_SQL | FLINK | BATCH / **STREAMING** |
| JAR | SPARK_JAR | — | BATCH |
| 流批 JAR | FLINK_JAR | — | BATCH / **STREAMING** |
| 脚本 | PYTHON / SHELL | — | BATCH |
| API | HTTP | — | BATCH |
| 数据集成 | SEATUNNEL | — | BATCH |
| 控制流 | CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT | — | Server 端执行 |

## 数据库

- 表结构定义:[`rudder-dao/src/main/resources/sql/schema.sql`](rudder-dao/src/main/resources/sql/schema.sql)(43+ 张 `t_r_*` 表)
- 主要分组:
  - **平台基础**: `workspace` / `workspace_member` / `project` / `user` / `audit_log`
  - **资源开发**: `script` / `script_dir` / `task_definition` / `task_instance` / `workflow_definition` / `workflow_instance` / `workflow_schedule`
  - **数据源 / 元数据**: `datasource` / `datasource_permission` / `metadata_config`
  - **发布 / 审批 / 版本**: `publish_record` / `approval_config` / `approval_record` / `version_config` / `version_record`
  - **服务注册**: `service_registry`
  - **SPI 配置**: `provider_config` / `runtime_config` / `file_config` / `result_config` / `notification_config`
  - **脱敏**: `redaction_rule` / `redaction_strategy`
  - **AI**: `ai_config` / `ai_session` / `ai_message` / `ai_skill` / `ai_mcp_server` / `ai_tool_config` / `ai_pinned_table` / `ai_dialect` / `ai_context_profile` / `ai_metadata_sync_config` / `ai_document` / `ai_document_embedding` / `ai_feedback` / `ai_eval_case` / `ai_eval_run`
  - **MCP**: `mcp_pat`(PAT bcrypt 哈希 + 前缀)/ `mcp_token_scope_grant`(token × capability 授权关系,带 PENDING_APPROVAL 状态)

## 快速开始

> Rudder 平台强依赖 **MySQL 8.x** 与 **Redis**。Hive / StarRocks / Trino / HDFS / Spark / Flink 等执行引擎由用户自行搭建或使用云厂商服务,再通过数据源管理接入。

### 方式一:本地开发

```bash
git clone https://github.com/Zzih/rudder.git && cd rudder
cp .env.example .env                                  # 配 MySQL / Redis / SSO / AI / RPC auth-secret 等

./mvnw clean package -DskipTests                      # 打 tarball
export $(grep -v '^#' .env | xargs)
bash rudder-dist/target/rudder-*-SNAPSHOT/api-server/bin/start.sh        # Server :5680/:5690
bash rudder-dist/target/rudder-*-SNAPSHOT/execution-server/bin/start.sh  # Execution :5681/:5691

cd rudder-ui && npm install && npm run dev            # 前端开发服务器
```

访问 **http://localhost:5680**。

### 方式二:Docker 镜像构建

```bash
./mvnw clean package -DskipTests   # 先生成 rudder-dist/target/rudder-<v>-SNAPSHOT.tar.gz

docker build -f rudder-dist/src/main/docker/api-server.dockerfile       -t rudder/api:dev .
docker build -f rudder-dist/src/main/docker/execution-server.dockerfile -t rudder/execution:dev .

docker run -d --name rudder-api       -p 5680:5680 -p 5690:5690 rudder/api:dev
docker run -d --name rudder-execution -p 5681:5681 -p 5691:5691 rudder/execution:dev
```

镜像基于 `eclipse-temurin:21-jre`,入口脚本来自 `rudder-dist/src/main/bin/`。

### 让 Cursor / Claude Desktop 接入 Rudder

1. Web IDE 里 → 个人设置 → **MCP Personal Access Token** → 选 capability,创建 token(WRITE 类需要 owner 审批后激活)
2. 把生成的 `rdr_pat_xxx` 填进 Cursor / Claude Desktop 的 MCP 配置:
   ```json
   {
     "mcpServers": {
       "rudder": {
         "url": "https://your-rudder-host/mcp",
         "headers": { "Authorization": "Bearer rdr_pat_xxx" }
       }
     }
   }
   ```
3. 重启外部 IDE,即可在其中调用 Rudder 工作空间能力(浏览元数据、查表、执行 SQL、改脚本、跑工作流……)

详细指南见 `rudder-service/rudder-mcp/src/main/resources/spi-guide/mcp-client-{cursor,claude-desktop,inspector}.{zh,en}.md`。

## 启动脚本

Maven 打包产物 `rudder-dist/target/rudder-<version>-SNAPSHOT.tar.gz` 解压后:

```
rudder-<version>/
├── bin/
│   ├── env.sh                环境变量
│   ├── start-server.sh       通用启动器
│   └── rudder-daemon.sh      daemon 模式
├── api-server/               Server 包(bin / conf / libs)
└── execution-server/         Execution 包(bin / conf / libs)
```

- **前台**(Docker 入口或 `RUDDER_FOREGROUND=true`):脚本 `exec` 替换为 java 进程
- **后台**:`nohup` + PID 文件,日志写入 `<server>/logs/`

## 端口与环境变量

| 进程 | HTTP | RPC | 关键 env |
|:-----|:----:|:----:|:--------|
| Server (rudder-api) | `RUDDER_API_PORT` (5680) | `RUDDER_RPC_PORT` (5690) | `RUDDER_REDIS_HOST/PORT/PASSWORD/DB`、`RUDDER_RPC_AUTH_SECRET`(≥32B)、`RUDDER_SSO_*` |
| Execution (rudder-execution) | `RUDDER_EXECUTION_PORT` (5681) | `RUDDER_RPC_PORT` (5691) | 同上 |

- MySQL / Redis 走标准 Spring 配置(`spring.datasource.*` / `spring.data.redis.*`)
- RPC `auth-secret` Server / Execution 必须一致
- 完整变量列表见 [`.env.example`](.env.example)

## 依赖服务

| 服务 | 用途 | 必需 |
|:-----|:-----|:----:|
| **MySQL 8.x** | 主数据库 + 服务注册 + 持久化 | ✓ |
| **Redis** | 流取消 pub/sub / 限流 / 预算 / TokenViewCache / 分布式缓存 | ✓ |
| Hive / StarRocks / Doris / ClickHouse / Trino | 数仓 / OLAP / 联邦查询 | |
| Spark / Flink / SeaTunnel | 计算 / 集成引擎 | |
| HDFS / OSS / S3 | 分布式存储 | |
| DataHub / OpenMetadata | 元数据中心 | |
| DolphinScheduler | 生产级调度(可对接发布) | |
| 向量库(pgvector / Qdrant) | RAG 知识库语义召回 | AI 启用时必需 |

## 文档

- 平台
  - [快速开始](docs/quickstart.md)
  - [配置参考](docs/configuration.md)
  - [部署指南](docs/deployment.md)
  - [架构总览](docs/architecture.md)
- 功能
  - [工作流](docs/workflow.md)
  - [任务类型](docs/task-types.md)
  - [数据源](docs/datasource.md)
  - [权限模型](docs/permissions.md)
  - [DolphinScheduler 集成](docs/dolphinscheduler.md)
- 进阶
  - [SPI 开发指南](docs/spi-guide.md)
  - [RPC 协议](docs/rpc.md)
  - [数据脱敏](docs/redaction.md)
  - [Runtime 适配器](docs/runtime-adapters.md)
  - [数据库 Schema](docs/database-schema.md)
- AI 模块
  - [AI 总览](docs/ai/README.md)
  - [快速上手](docs/ai/quickstart.md)
  - [用户指南](docs/ai/user-guide.md)
  - [架构](docs/ai/architecture.md)
  - [LLM / Embedding / Vector Provider](docs/ai/providers.md)
  - [Skill 与 Prompt](docs/ai/skills-prompts.md)
  - [Tools 与 MCP](docs/ai/tools-mcp.md)
  - [知识库与 RAG](docs/ai/knowledge-base.md)
  - [离线评测](docs/ai/eval.md)
  - [运维](docs/ai/operations.md)
  - [故障排查](docs/ai/troubleshooting.md)
- 安全
  - [安全总览](docs/security/README.md)
  - [JWT 与登录会话](docs/security/jwt.md)
  - [SSO(OIDC + LDAP)](docs/security/sso.md)
  - [审计日志](docs/security/audit.md)
  - [密钥轮转](docs/security/rotation.md)

## 项目截图

**工作空间首页** — 登录后落地页:工作空间网格 + 概览统计 + 可配置文档入口

![首页](docs/image/RUDDER_HOME.png)

**项目空间** — 单个工作空间内的项目列表

![项目](docs/image/RUDDER_RPOJECT.png)

**Web IDE** — 在线 SQL/脚本编辑、元数据补全、即席查询

![IDE](docs/image/RUDDER_IDE.png)

**工作流编排** — 基于 AntV X6 的 DAG 拖拽编排,4 类控制流节点

![Workflow](docs/image/RUDDER_WORKFLOW.png)

**文件管理** — 文件存储 SPI(LOCAL / OSS / S3 / HDFS)统一入口

![Files](docs/image/RUDDER_FILE.png)

**MCP Token 管理** — PAT + Capability + 二级审批,对接 Cursor / Claude Desktop

![MCP](docs/image/RUDDER_MCP.png)

**AI 配置** — LLM / Embedding / Vector / Rerank 多 provider 切换

![AI Config](docs/image/RUDDER_AI_MANAGEE.png)

**平台管理** — 用户、认证源、数据源、审计日志、各 SPI 配置一站式入口

![Admin](docs/image/RUDDER_MANAGER.png)

**脱敏规则** — 平台级 PII 规则 + 策略,QueryResult / 日志全局生效

![Redaction](docs/image/RUDDER_REDACTION_MANAGER.png)

**Runtime 配置** — LOCAL / 阿里云 / AWS 多 Runtime provider 在线配置

![Runtime](docs/image/RUDDER_RUNTIME_MANAGER.png)

## 致谢

Rudder 在设计与实现过程中,从开源社区借鉴了大量经过生产检验的设计思路。特别要感谢:

- **[Apache DolphinScheduler](https://dolphinscheduler.apache.org/)** — Rudder 的工作流模型、任务类型抽象、4 种控制流节点(CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT)、`AbstractParameters` 风格的 IN/OUT 参数与 varPool 语义、`BIZ_DATE` 等内置参数变量、Server / Worker 分离 + 服务注册的拓扑思路、`WorkflowInstanceRunner` 与 `TaskInstance` 状态机,均深度借鉴了 DolphinScheduler 的成熟设计。在此向 DS 社区致敬,他们对调度领域的工程沉淀让一个小团队也能站在巨人的肩膀上做事。
- **[Spring AI](https://spring.io/projects/spring-ai)** — `ChatClient` / `Advisor` 链路 / `ToolCallback` 工具框架 + Spring AI MCP Server 是 Rudder AI 模块的基石,让我们能聚焦于业务编排与 capability 安全模型,而非协议适配。
- **[DataHub](https://datahubproject.io/) / [OpenMetadata](https://open-metadata.org/)** — 成熟的元数据治理与 PII 分类模型,Rudder 的 `MetadataProvider` SPI 字段定义直接对照它们设计,可以无缝接入这两套元数据中心。

以及所有 Rudder 依赖的优秀开源项目 —— Spring Boot、Vue、Element Plus、Monaco Editor、AntV X6、MyBatis-Plus、Netty、Reactor、Quartz、Anthropic MCP、pgvector、Qdrant 等 —— 没有这些社区多年的积累,一个人不可能独立做起这种规模的全栈项目。完整依赖见 [`pom.xml`](pom.xml) 与 [`rudder-ui/package.json`](rudder-ui/package.json)。

## 项目背景

> 以下是开发者的个人手记,放在文档末尾,供有兴趣的读者参考。

我从事数据开发工作已有 8 年,接触大数据也有 6、7 年了。这些年里经历过手搭 Hadoop 集群、迁移云平台、对接各种调度系统,踩过的坑、绕过的弯路,慢慢沉淀成了对"一个好用的数据平台应该长什么样"的思考。

这个项目源于 5、6 年前的一个构想——做一个真正好用的开源大数据 IDE。但一个人的精力有限,后端架构想得再清楚,前端的 IDE 交互、DAG 编辑器、工作流可视化这些体验层的东西始终是瓶颈,项目也就一直停留在构想阶段。

直到今天,AI 辅助编程已经足够成熟,尤其在前端开发领域——复杂的组件交互、UI 细节打磨,AI 都能高效配合完成。这让一个人也有能力去撑起一个完整的全栈项目。所以我决定不再等了,把这些年的积累和思考付诸实践。

### 大数据部门的尴尬定位

大数据部门在企业中的定位一直比较割裂:小公司不需要,成本也承受不住;头部大公司能承受大数据的成本,但它们早已有了自己的云服务产品(DataWorks、EasyData 等)。真正痛苦的是夹在中间的那一批——有一定规模、有大数据部门的公司。

它们面临的困境是:

- **云平台不适配** — 想用云厂商的大数据平台(DataWorks、EasyData...),但发现和自己公司的业务流程、权限体系、审批规范对不上,二次开发的成本甚至比自建还高
- **开源项目太臃肿** — 看看 DataSphere Studio 等开源项目,功能确实全面,但模块太多、概念太重、部署运维复杂,很多功能用不上却不得不背着
- **自研周期长** — 从零搭一套数据平台,后端的任务调度、工作流引擎、多引擎适配已经够复杂了,再加上前端的 IDE 编辑器、DAG 可视化编排、权限管理界面,一个小团队很难在合理的时间内交付一个可用的产品

所以市场上缺的不是又一个大而全的数据平台,而是一个**轻量、现代、易于扩展**的数据 IDE——开箱够用,不够的地方可以自己插件化扩展,而不是一上来就给你塞一堆用不上的东西。

### 技术变迁带来的痛苦

回看过去几年大数据团队经历的技术变迁:

1. 最早大家买云服务器,手动搭建 Hadoop/YARN 集群
2. 慢慢地开始使用云厂商提供的 EMR
3. 因为各种原因从一家云厂商迁移到另一家云厂商
4. 最终因为成本和运维压力,又开始将任务迁向 Serverless 服务

每一次技术切换,都意味着数据平台的功能要改来改去,代码越来越割裂,技术债越积越多。我们需要的是一个**可以在任何云上都能适配的数据平台**——所以 Rudder 把执行环境抽成 **Runtime SPI**:无论集群跑在 AWS EMR、阿里云 Dataproc 还是 Serverless Spark 上,只要适配对应的 Runtime 插件,上层任务调度、工作流编排、IDE 体验都不用动。

### 流批一体

传统的数据平台往往将批处理和流处理割裂为两套系统、两套开发模式、两套运维体系。Rudder 在设计之初就通过统一的 TaskChannel SPI 将流和批纳入同一个平台:批任务(Hive / Spark / SQL / Python / Shell / SeaTunnel)通过 DAG 工作流编排和 Cron 调度;流任务(Flink SQL / Flink JAR)在同一个 IDE 中开发和提交,通过独立的生命周期管理进行启停和监控。开发者在一个平台上完成所有数据任务的开发、管理和运维,不再需要在多套系统之间来回切换。

### AI 原生 + MCP 双向

AI 是 Rudder 的**平台一等公民**,不是外挂插件:Tool Calling Agent、Skill 体系、RAG 知识库、反馈闭环、离线评测,深度嵌入到 IDE、元数据、数据源、权限、审计的每一层。LLM、Embedding、Vector Store 三类核心 AI 基础设施都是独立 SPI,可以按需切换 Claude / OpenAI / DeepSeek / Ollama / 智谱 / pgvector / Qdrant。

更进一步,Rudder 把 MCP 做成了**双向公民**:既作为 MCP 客户端汇聚外部 server 的工具,也作为 MCP 服务端把工作空间能力反向暴露给 Cursor / Claude Desktop / Inspector 等任意外部 AI IDE。配合 PAT + Capability + 审批 三层安全模型,做到"工作空间能力跟着用户走"——你在 Cursor 里直接让 Claude 帮你查 Rudder 工作空间的元数据、采样、改脚本、跑工作流,整个调用链全程审计、全程脱敏、敏感操作走二级审批。

### 关于名字

Rudder,舵。希望每一位使用这个平台的人,都能在数据的海洋中自由掌舵。

## 参与贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/your-feature`)
3. 提交更改 (`git commit -m 'Add some feature'`)
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

## 联系

- GitHub Issues: [https://github.com/Zzih/rudder/issues](https://github.com/Zzih/rudder/issues)

## License

[Apache License 2.0](LICENSE)
