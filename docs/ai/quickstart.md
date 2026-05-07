# 快速开始

> 目标:10 分钟内跑通 Rudder AI 的"能聊天"状态。RAG / 工具 / 知识库可以之后再开。

## 前置依赖

### 必需

```bash
# MySQL 8+
docker run -d --name rudder-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=rudder \
  -e MYSQL_DATABASE=rudder \
  mysql:8

# Redis 7+
docker run -d --name rudder-redis -p 6379:6379 redis:7
```

这两个是**平台强依赖**,MySQL 存所有业务数据,Redis 存:
- Metadata 缓存
- Stream / Tool approval 信号广播
- 限流计数器
- 分布式锁

### 可选(决定 AI 能力)

| 组件 | 影响 |
|------|------|
| Vector DB(Qdrant / pgvector) | 没配 → RAG 走 MySQL FULLTEXT 降级,语义检索精度差 |
| Embedding provider | 没配 → 文档入库不生成向量,只能靠 FULLTEXT |
| 元数据 provider(JDBC / DataHub / OpenMetadata) | 没配 → Agent 无法查表结构,只能靠用户 Pin 表 |

**Qdrant**(最推荐):

```bash
docker run -d --name rudder-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

**pgvector**(有已存 PG 就复用):

```bash
docker run -d --name rudder-pgvector -p 5432:5432 \
  -e POSTGRES_PASSWORD=pg pgvector/pgvector:pg16
```

## 配置 Rudder

编辑 `.env` 或 `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/rudder?useSSL=false
    username: root
    password: rudder
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

数据库 schema 文件位于 `rudder-dao/src/main/resources/sql/schema.sql`,首次部署需手动执行(或接 Flyway / Liquibase)。

## 启动 + 登录

```bash
cd rudder
mvn -pl rudder-api -am package -DskipTests
java -jar rudder-api/target/rudder-api-*.jar
```

浏览器打开 `http://localhost:5680`(按你的 `server.port` 走),用 `admin/admin` 登录。

## 配第一个 LLM

顶部头像 → **管理** → AI 配置 → **AI 大模型** tab(后端 `POST /api/config/ai-llm`)。

选一个 provider,按下表填最少参数:

| Provider | 关键参数 |
|----------|---------|
| **CLAUDE** | `apiKey`, `model=claude-sonnet-4-...`, `baseUrl=https://api.anthropic.com` |
| **OPENAI** | `apiKey`, `model`, `baseUrl=https://api.openai.com/v1`(或任何 OpenAI 协议兼容网关) |
| **DEEPSEEK** | `apiKey`, `model=deepseek-chat`, `baseUrl=https://api.deepseek.com` |
| **OLLAMA** | `baseUrl=http://localhost:11434`, `model=llama3.1`(无需 apiKey) |

> 当前 LLM SPI 仅支持 Claude / OpenAI / DeepSeek / Ollama 四种。智谱 GLM 仅作为 Embedding provider 可用。

点 **测试连接** → 绿色 → 保存。

> 保存会触发 `LlmPluginManager.updateActiveClient()`,**立即**关旧 client 开新 client,不重启进程。

## 第一次对话

1. 左侧导航 → 任意一个 workspace → 进 IDE
2. 右侧栏找到 **MagicStick** 图标 → 点开 AI 面板
3. 顶部开关选 CHAT 模式
4. 输入 "你好,介绍下自己" → 回车
5. 看到流式输出即表明链路通

## 下一步

| 想要的能力 | 看哪章 |
|-----------|-------|
| AI 能查我的表结构 | [知识库 & RAG](knowledge-base.md) |
| AI 能执行 SQL / 改文件 | [工具 & MCP](tools-mcp.md) + 切到 **AGENT** 模式 |
| 问答质量提升 | 配 [Embedding + Vector](providers.md),跑一次元数据同步 |
| 多 workspace 配不同 AI 行为 | [Skill & Prompt](skills-prompts.md) 的 Context Profile 节 |
| 审计 / 合规 | 脱敏文档另行维护(平台级能力,代码在 `rudder-common` + `service.redaction.RedactionService`) |

## 常见坑

| 症状 | 原因 |
|------|------|
| 登录后看不到 "管理" 入口 | 账号不是 SUPER_ADMIN |
| 对话一发就报"AI 未配置" | 没在 Admin → AI 大模型 保存过 provider |
| 流式卡住不出 token | 通常 provider baseUrl 不通,或 Ollama 没 `ollama serve` |
| 502 / timeout | 网络 / 防火墙问题 |
