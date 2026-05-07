# Rudder AI 模块操作文档

> 底层统一基于 **Spring AI 2.0.0-M4**(`ChatClient` + `Advisor` + `ToolCallback`),所有 provider / vector / embedding 可在 Admin UI 热切换,无需重启。Spring Boot 4.0.5。

## 导航

### 按角色

| 角色 | 从哪里开始 |
|------|----------|
| 第一次部署,想跑通 | [快速开始](quickstart.md) |
| 平台管理员(配置 + 运维) | [Provider 配置](providers.md) → [知识库](knowledge-base.md) → [运维](operations.md) |
| 普通开发者(日常使用) | [开发者指南](user-guide.md) |
| AI 工程师(调试 / 评测) | [Skill & Prompt](skills-prompts.md) → [评测](eval.md) |
| 架构师 / 代码维护者 | [架构深入](architecture.md) |
| 排障 | [故障排查](troubleshooting.md) |

### 按主题

| 文档 | 内容 |
|------|------|
| [快速开始](quickstart.md) | Docker 起 MySQL/Redis/Qdrant,最小配一个 LLM 就能用 |
| [Provider 配置](providers.md) | LLM / Vector / Embedding 三类 provider 详细配置 |
| [知识库 & RAG](knowledge-base.md) | 手工文档 + 元数据同步 + 向量检索 + DataHub/OpenMetadata 接入 |
| [工具 & MCP](tools-mcp.md) | 内置工具 + MCP server 接入 + 工具权限覆盖 + 审批流 |
| [Skill & Prompt](skills-prompts.md) | Skill 定义 + Dialect 方言 + ContextBuilder 注入机制 + Context Profile |
| [开发者指南](user-guide.md) | Chat vs Agent 模式 + 审批交互 + Pin 表 |
| [评测 (Eval)](eval.md) | 回归用例设计 + 三组断言(文本/工具/性能) + Demo case |
| [运维日常](operations.md) | 刷缓存 / 重索引 / 孤儿清理 / 监控指标 |
| [故障排查](troubleshooting.md) | 对话 / 工具 / RAG / 取消 / eval 各类问题处理 |
| [架构深入](architecture.md) | 请求链路 + 模块边界 + 数据模型 + 一致性保障设计 |

## 架构一图流

```
浏览器
   │  POST /api/ai/sessions/{id}/turns (SSE)
   ▼
AiTurnController(virtual thread)
   │
   ▼
AiOrchestrator  ─按 session.mode 分派─>  AgentExecutor(AGENT 模式,带工具)
                                       TurnExecutor (CHAT 模式,纯对话)
   │
   ├─ MessagePersistence  ── insert user / assistant 消息
   ├─ StreamRegistry      ── 注册可取消 handle(Caffeine + Redis pub/sub)
   ├─ ContextBuilder      ── 拼 system prompt(base-role / dialect / pinned-tables / ...)
   ├─ ChatClientFactory   ── Spring AI ChatClient + Advisor 链
   │                          ├─ SimpleLoggerAdvisor
   │                          ├─ RudderRagAdvisor      (向量检索注入)
   │                          ├─ RedactionAdvisor      (PII 脱敏)
   │                          └─ UsageMetricsAdvisor   (token 累计)
   ├─ ToolRegistry        ── 20 native + workspace 可见 MCP 工具 + Skill-as-tool
   │                          每个 tool 包一层 RudderToolCallback
   │                          ├─ PermissionGate        (角色校验 + readOnly)
   │                          ├─ ToolApprovalRegistry  (写类工具等待用户批准)
   │                          └─ 落 tool_call / tool_result 消息
   │
   ▼
Spring AI ChatClient.stream()
   │ ← LLM 返回 token / tool_use
   │
   ▼
TurnEvent stream → SSE → 浏览器
```

## 支持的外部系统

| 类型 | 可选 provider |
|------|-------------|
| LLM | **Claude**(Anthropic 原生) / **OpenAI**(协议兼容) / **DeepSeek**(原生) / **Ollama**(本地) |
| Vector | **Qdrant**(首选) / **PgVector**(复用 PG) / **Local**(兜底走 MySQL FULLTEXT) |
| Embedding | **OpenAI 协议**(OpenAI / 阿里百炼 / Ollama 兼容) / **Zhipu** |
| Metadata | **JDBC**(直连 DB 的 DatabaseMetaData) / **DataHub** / **OpenMetadata** |
| MCP | **STDIO** 本地子进程 / **HTTP_SSE** 远程 server |

> SPI 模块对应 `rudder-spi/rudder-llm`、`rudder-spi/rudder-vector`、`rudder-spi/rudder-embedding`、`rudder-spi/rudder-metadata`,均按 `-api` / `-<provider>` 拆分。

## 热切换时效

| 改动 | 生效 |
|------|------|
| Provider(LLM / Vector / Embedding / Metadata) | **立即**,下一个 turn 走新 client |
| Tool Permission / Context Profile / Skill / MCP / 脱敏规则 | **立即** |
| 知识库文档 | 入库即可召回(需 Embedding 已配) |
| 元数据同步 | 按 cron 或手工触发 |
| 数据源 name | **不可变**(参与 DataHub URN / OpenMetadata FQN,改名会断连接) |

## 一句话总结

**最小可用**:Docker 起 MySQL + Redis + 一个 LLM API key → 跑起来就能聊天。
**高质量答案**:加 Embedding + Vector + 元数据同步 → AI 知道你的表结构。
**让 AI 干活**:开 Agent 模式 + 配工具权限 → AI 可以读写脚本 / 执行 SQL(经审批)。
