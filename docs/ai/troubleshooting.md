# 故障排查

> 按现象查。先读 **快速定位**,找到大类后跳到具体章节。

## 快速定位

| 现象 | 看哪节 |
|------|-------|
| 一点对话都发不出 / "AI 未配置" / 502 timeout | [对话发不出](#对话发不出) |
| 对话能发,但 AI 回答不着边际 / 字段名错 / SQL 方言错 | [回答质量差](#回答质量差) |
| 工具调用失败 / 审批弹不出来 / 权限错 | [工具 & 审批](#工具--审批) |
| 取消按钮按了没效果 | [取消不生效](#取消不生效) |
| 知识库配了但没被召回 | [RAG 召回差](#rag-召回差) |
| eval 全挂 / eval 结果不准 | [eval 问题](#eval-问题) |
| 管理后台操作某 provider 报错 | [Provider 配置](#provider-配置) |
| 数据源名改完 AI 就搜不到表了 | [DataHub/OpenMetadata 匹配](#datahub--openmetadata-匹配) |

---

## 对话发不出

| 症状 | 排查步骤 |
|------|---------|
| **AI 未配置** | Admin → AI 大模型 → 是否有保存过任一 provider |
| **502 / timeout** | 1. `curl <baseUrl>/v1/models` 检查 baseUrl 通否<br>2. Ollama 场景确认 `ollama serve` 在跑<br>3. 公司代理需要在配置里填 `proxy` 字段 |
| **一直转圈无响应** | 看后端日志 `AiTurnController` 相关 ERROR;常见是 provider apiKey 无效 |
| **回英文** | 模型默认偏好。在 session 的 `systemPromptOverride` 填"始终使用中文回答" |
| **Claude 流式慢** | Anthropic API 本身偏慢,或 maxTokens 调太大 |
| **NoToolCallbackException / IllegalStateException: No ToolCallback found for tool name** | 已修:LLM 幻觉工具名时返回"不存在"让 LLM 自纠 |

## 回答质量差

| 症状 | 原因 & 做法 |
|------|-----------|
| 字段名错 | 没配知识库 / 没 Pin 表。**解**:跑元数据同步让 SCHEMA 入库,或 Pin 目标表 |
| SQL 方言错(用了 Trino 语法但引擎是 StarRocks) | 检查 Dialect 配置 + TaskType 是否正确传入。Admin → AI 配置 → 方言,看该 TaskType 的内容是否完整 |
| 引用了别家引擎的表 | engineType 过滤失效。检查 SCHEMA 文档的 engineType 字段是否设对 |
| 回答太泛 | RAG 没开。Context Profile 把 `injectWikiRag=true` |
| 用的是旧 schema | 元数据同步没跑;或数据源 DDL 改后没触发 invalidateCache。**解**:手工"立即同步"对应数据源 |
| 回答重复或绕圈 | 模型本身问题;试换模型(Claude → GPT-4o)或调低 temperature |

## 工具 & 审批

| 症状 | 原因 |
|------|------|
| `tool xxx not allowed in read-only mode` | workspace 被置 readonly。Admin → AI 配置 → 工具权限 覆盖 |
| `User rejected the operation` | 你点了 Reject,或 **5 分钟超时** |
| `Tool execution exception: ...` | 工具代码层出错,看后端日志栈 |
| 工具执行成功但 tab 没刷 | 你当前 tab 有未保存改动,保护性跳过 |
| AGENT 模式看不到某工具 | 工具权限配置里 `enabled=false`;或该 workspace 不在 MCP server 的可见 workspace 列表 |
| MCP 工具不可用 | Admin → MCP 服务器 → 看 `healthStatus`。DOWN 的工具会被临时过滤,可点 `POST /api/ai/mcp/refresh-health` |
| 审批弹窗不出来 | 前端 SSE 断开,检查浏览器 Network;或后端 RudderToolCallback 没 emit ToolCall 事件 |
| 点了 Apply 但后端没响应 | 多节点部署 + 请求落别的节点:`ToolApprovalRegistry` 现已走 Redis pub/sub,如果仍失败查 Redis 连接 |

## 取消不生效

**日志关键字**:
- `stream {} cancelled on node` — 本节点 handle 已标记
- `broadcast cancel for stream` — 跨节点广播已发
- `cancel stream {} but not found on this node` — 该节点没持有这个 handle(需广播找到正确节点)

**AGENT 模式**工具正在执行途中不能瞬时中断,会等当前工具完成。工具内循环(如 `run_sql_readonly`)**不会**检查 cancel flag,只有下次进入工具入口才检查。

## RAG 召回差

| 症状 | 诊断 |
|------|------|
| 完全没召回 | 1. Admin → 知识库 → 看某文档 `indexedAt` 有没有时间戳 — null = 未向量化<br>2. 未向量化 → 没配 Embedding 或 embed 报错(看后端日志)<br>3. 已向量化但查不到 → Qdrant collection 存在?`curl http://qdrant:6333/collections/` |
| 召回无关 | 1. Chunk 太大 / 太杂 → 拆细<br>2. Embedding 模型不适合中文 → 换 `bge-m3` 或类似<br>3. topK 太大噪声 → 调小 |
| 中文语义差 | MySQL FULLTEXT 降级模式下中文 ngram 精度有限。**必配** Qdrant / PgVector |
| 跨 workspace 看不到共享文档 | SCHEMA / WIKI 入库时 `workspaceId` 必须为 NULL 才是平台共享 |

## eval 问题

| 症状 | 原因 |
|------|------|
| 所有 case 执行都报 provider 错误 | 没配 provider |
| 所有 case 都超时 | provider baseUrl 不通 / maxTokens 太大 |
| `mustCallTools missing: xxx` | 工具没进 system prompt / AGENT 模式没打开 / 工具 description 不够清晰引导 AI 使用 |
| `sqlPattern mismatch` 但 finalText 看着对 | 正则写太严 — 试 `mustContain` 代替 |
| 同一 case 一会 pass 一会 fail | LLM 本身的概率性,断言写太严;加 `mustContain` 替代完整正则 |
| 某 case 突然从 pass 变 fail | 检查是否 provider 升级了模型、数据源 DDL 变了、prompt 模板变了 |

详见 [评测](eval.md)。

## Provider 配置

| 症状 | 原因 |
|------|------|
| 保存时 "测试连接失败" | apiKey 错 / baseUrl 错 / 模型名错 |
| Ollama 保存 OK 但对话 404 | Ollama 版本太老不支持 `/v1/chat/completions`,升级 Ollama |
| 换 Embedding 后 RAG 完全不准 | 维度变了,需要重建 collection + 重索引,见 [运维 - 换 Embedding 模型](operations.md#换-embedding-模型) |
| Qdrant "NOT_FOUND: Collection xxx doesn't exist" | 正常 — collection 没自动创建过(没入库就检索)。入库一次会自动 ensureCollection |
| DataHub `platformInstance` 精确匹配失效 | 要求 DataHub recipe 里配 `platform_instance: <Rudder 数据源 name>`,且 `Datasource.name` 不能改过 |
| 想配 Zhipu 当 LLM | 当前不支持 —— 仅 Claude / OpenAI / DeepSeek / Ollama 四种 LLM provider。Zhipu 仅作 Embedding |

## DataHub / OpenMetadata 匹配

| 症状 | 原因 |
|------|------|
| 数据源改名后 AI 看不到表 | `Datasource.name` 是 DataHub URN / OpenMetadata Service FQN 的一部分。修改 name = 和对端脱钩。UI 已禁 edit name,但如果绕过后端约束直接改 DB 需手动同步对端 |
| DataHub 里表存在但 Rudder 拉不到 | 降级到平台级视图时,同类型所有 ds 会看到一样的表。想精确隔离 → DataHub 配 platform_instance |
| OpenMetadata service 名对不上 | Rudder `Datasource.name` 必须和 OpenMetadata Database Service `name` 精确相等(大小写敏感) |

## 多节点部署常见问题

| 症状 | 原因 |
|------|------|
| 某些 session 历史查不到 | MySQL 主从延迟;或不同节点连不同库 — 检查 datasource 配置 |
| 流 cancel 部分工作部分不工作 | 多节点但 Redis pub/sub 未配 → StreamRegistry 降级单节点模式 |
| 工具审批超时但用户点了 Apply | 见 [工具 & 审批](#工具--审批) |
| 元数据缓存某节点看到老值 | 已修:统一走 Redis 缓存,不再本地 Caffeine + DB 事件。如果仍出现,检查 Redis 连接 |

## 日志关键字速查

| 关键字 | 说明 |
|--------|------|
| `agent turn {} failed` | AGENT 模式异常 |
| `chat turn {} failed` | CHAT 模式异常 |
| `tool {} failed` | 具体工具执行失败 |
| `broadcast cancel for stream` | 跨节点取消 |
| `DataHub 未找到 platform_instance={}` | DataHub 降级 |
| `sweep cleaned {} orphan embeddings` | 孤儿向量清理 |
| `metadata sync done datasource={}` | 元数据同步成功 |
| `eval case {} execution failed` | eval 执行异常 |

## 求助

如果以上都不解决:

1. 翻 `rudder-api/target/logs/` 下最新日志
2. Admin → 审计日志 → 查过去 10 分钟的 ERROR 条目
3. 查 `t_r_ai_session` 找 status=FAILED 的 session,取 id 反查日志
4. 仍不解决联系 AI 组
