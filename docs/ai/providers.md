# Provider 配置

Rudder 的 AI 能力由 **三类 provider** 组合:LLM(对话)、Vector(向量库)、Embedding(文本向量化)。每一类都是 SPI 插件,可在 Admin UI 热切换。

Admin → AI 配置 → 对应 tab。后端配置端点统一收口在 `ConfigController`,路径分别为:
- `POST /api/config/ai-llm`
- `POST /api/config/ai-embedding`
- `POST /api/config/ai-vector`
- `POST /api/config/metadata`

三种 AI 配置(LLM / EMBEDDING / VECTOR)共用 `t_r_ai_config` 表,由 `AiConfigType` 列鉴别。

## A. LLM(对话模型)

### 支持的 provider

SPI 模块在 `rudder-spi/rudder-llm/`,包含 `rudder-llm-api` 契约 + 四种实现:

| providerType | SPI 模块 | 底层 driver | 适用场景 |
|--------------|---------|-----------|---------|
| **CLAUDE** | rudder-llm-claude | spring-ai-anthropic | Anthropic Claude 原生协议,最稳 |
| **OPENAI** | rudder-llm-openai | spring-ai-openai | OpenAI / DeepSeek 兼容模式 / Moonshot / Qwen / 自建 vLLM / 阿里百炼 |
| **DEEPSEEK** | rudder-llm-deepseek | spring-ai-deepseek | DeepSeek 原生 |
| **OLLAMA** | rudder-llm-ollama | spring-ai-ollama | 本地 / 内网 Ollama,离线部署 |

> 当前没有 Zhipu LLM provider —— 智谱 GLM 仅作为 Embedding provider 可用。如需接入需新增 SPI 模块,见 [架构 - 扩展点](architecture.md)。

### 关键配置字段

| 字段 | 说明 |
|------|------|
| `apiKey` | 除 Ollama 外都必填 |
| `baseUrl` | 默认走官方地址。自建 / 代理时填你的网关 URL |
| `model` | 确切模型名,受 provider 约束;填错会在 `测试连接` 时暴露 |
| `maxTokens` | 单次生成上限。太小会被截断(UI 会提示),太大浪费 token |
| `temperature` | 0-1,写类任务建议 0.1-0.3 |
| `timeout` | 默认 60s;大 prompt + 深度推理模型可能需要更长 |

### 切换不重启

保存新配置时后端调 `LlmPluginManager.updateActiveClient(provider, config)`:
1. 原子替换 active 引用
2. `closeSilently()` 旧 client(释放连接池)
3. **下一个 turn 立即走新 client**

所以调优 baseUrl / 测不同模型,不用发版。

### 高级用法

**同一 provider 跑不同模型**:目前一个 active client 只持有一个 model,想 A/B 测试建议起两套环境。或改 `ChatClient.prompt().options(OpenAiChatOptions.builder().model("xxx"))` —— 需要改代码,不支持从 UI 按 session 覆盖。

**Ollama 流式**:`stream=true` 由 provider 代码控制,UI 上无开关。Ollama 流式有时需要模型本身支持,`llama3.1` 支持,老 `mistral:7b` 可能不支持。

## B. Vector(向量库)

### 支持的 provider

SPI 模块在 `rudder-spi/rudder-vector/`:

| providerType | SPI 模块 | 何时用 |
|--------------|---------|-------|
| **QDRANT** | rudder-vector-qdrant | 首选。gRPC 6334,性能和精度都最好 |
| **PGVECTOR** | rudder-vector-pgvector | 已经有 PG 的环境复用,省一套运维 |
| **LOCAL** | rudder-vector-local | 占位不做真实向量检索,RAG 降级走 MySQL FULLTEXT |

### Collection 自动管理

```
collection name:  {workspaceId}_{docType}
例:
  0_SCHEMA          平台级 SCHEMA 文档(元数据同步产出,workspaceId=NULL 归到 0)
  3_WIKI            workspace 3 的 wiki 文档
  3_RUNBOOK         workspace 3 的 runbook
```

文档第一次入库时触发 `vectorStore.ensureCollection(name, dim)`,维度由当前 Embedding 决定。**切换 Embedding 后 dim 变,需要重新建 collection**(见 [运维](operations.md))。

### 不配 Vector 的降级行为

- 入库:只存 MySQL(`t_r_ai_document` 有 `indexedAt = null` 表示未向量化)
- 检索:`DocumentRetrievalService.retrieve()` 走 MySQL FULLTEXT,中文分词是 ngram,精度有限
- 日志里看到语义检索为空、回落 FULLTEXT 即降级中

### Qdrant 健康

- `isHealthy()` 走 `/readyz` 端点
- `NOT_FOUND: Collection 'xxx' doesn't exist` 不算致命,表明还没入库就被检索,返回空即可

## C. Embedding

### 支持的 provider

SPI 模块在 `rudder-spi/rudder-embedding/`:

| providerType | SPI 模块 | 说明 |
|--------------|---------|------|
| **OPENAI** | rudder-embedding-openai | 最通用。兼容 OpenAI / 阿里百炼 / Ollama(OpenAI 兼容模式)/ 自建 bge |
| **ZHIPU** | rudder-embedding-zhipu | 智谱 embedding-3 |

Ollama 的 embedding 目前**没有**原生 provider —— 可以用 `baseUrl=http://localhost:11434/v1` 配成 OpenAI 兼容模式接入。

### 关键字段

- `apiKey` / `baseUrl` / `model`(如 `text-embedding-3-small` / `bge-m3`)
- `dimensions`:不同模型的输出维度不一样(OpenAI 小模型 1536 / bge-m3 1024 等)

### 切换模型后要做的事

维度不同 → 老 collection 的向量跟新查询向量不兼容 → **必须重建 collection**。

动作:
1. 保存新 Embedding 配置
2. 去 Admin → 知识库 → 点"全部重索引"(或按 docType 分别重索引)
3. 等重索引完成(看 `indexedAt` 有没有新时间戳)

## D. Metadata Provider

虽然不算 AI 直接依赖,但影响 AI 看到的表结构质量。SPI 模块在 `rudder-spi/rudder-metadata/`,实现:`rudder-metadata-jdbc` / `rudder-metadata-datahub` / `rudder-metadata-openmetadata`。配置见 [知识库 & RAG](knowledge-base.md#元数据-provider)。

## 组合推荐

| 场景 | LLM | Embedding | Vector | Metadata |
|------|-----|-----------|--------|---------|
| **最简可用** | 任一 | — | — | JDBC |
| **推荐配置** | Claude / OpenAI | OpenAI text-embedding-3-small | Qdrant | JDBC |
| **完全离线** | Ollama + llama3.1 | Ollama bge-m3(OpenAI 兼容接入) | Qdrant(自部署) | JDBC |
| **企业合规** | 自建 vLLM + 国产模型 | 自建 bge(OpenAI 兼容接入)/ Zhipu | PgVector(复用 PG) | OpenMetadata / DataHub |

## Provider 故障排查

参见 [故障排查](troubleshooting.md#provider-配置)。
