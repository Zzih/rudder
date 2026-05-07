# 运维日常

> 日常维护动作:缓存、索引、孤儿清理、监控、迁移。

## 定期任务

### 元数据同步

每个配了 cron 的 `t_r_ai_metadata_sync_config` 按点执行,`MetadataSyncScheduler` 负责。

**检查同步是否正常**:Admin → AI 配置 → 知识库 → 表结构同步区域,看每行的 `lastSyncStatus` 和 `lastSyncAt`。

- `SUCCESS` / 时间是最近 → 正常
- `FAILED` → 点击看 `lastSyncMessage`
- `RUNNING` 很久没变 → 可能进程崩溃,手动"立即同步"重启

## 手工触发动作

### 立即同步某数据源

Admin → AI 配置 → 知识库 → 同步表某行 → "立即同步" 按钮(`POST /api/ai/metadata-sync/sync/{datasourceId}`)。阻塞同步直到完成,返回 `inserted/updated/skipped/deleted` 统计。

### 全部重索引

换了 Embedding 模型、Vector collection 维度不兼容时必做。

Admin → AI 配置 → 知识库 → 文档区域右上 → "全部重索引"(`POST /api/ai/documents/reindex`,可按 docType 过滤)。

流程:
1. 遍历 `t_r_ai_document`
2. 每篇 → `deleteVectors` → 重新 chunk + embed → `vectorStore.upsert`
3. 期间 RAG 查询可能暂时召回不全

### 清 Redis 缓存

数据源连接信息 / 元数据 / 用户会话状态等缓存在 Redis。强制刷:

**命令行**:

```bash
# 清某数据源的 metadata 缓存(MetadataClient.invalidateCache)
redis-cli DEL $(redis-cli KEYS "rudder:meta:{datasourceName}:*")

# 清所有 metadata 缓存(核武器,慎用)
redis-cli DEL $(redis-cli KEYS "rudder:meta:*")
```

**代码内**:
- 改了数据源配置 → 自动触发 metadata invalidate
- 管理员页改了某数据源 → 同上
- 元数据同步开始前 → `metadataClient.invalidateCache(dsName)`

### 孤儿向量清理

**自动**:每次元数据同步结束,`DocumentIngestionService.sweepOrphanEmbeddings(2000)` 自动扫。

**手工大扫除**:目前没有专门的 admin 端点,可在代码里调:

```java
documentIngestionService.sweepOrphanEmbeddings(Integer.MAX_VALUE);
```

作用:找出 `t_r_ai_document_embedding` 里 documentId 指向已不存在 AiDocument 的行,按 pointId 从 Qdrant 清 + 删 embedding 行。

### 换 Embedding 模型

维度不同会导致 Qdrant 查询报错。标准流程:

1. Admin → AI 配置 → Embedding → 保存新配置(`POST /api/config/ai-embedding`)
2. **删除旧 collection**(Qdrant):
   ```bash
   curl -X DELETE http://qdrant:6333/collections/0_SCHEMA
   curl -X DELETE http://qdrant:6333/collections/0_WIKI
   # ... 其他 workspace/docType
   ```
   (或用 Qdrant Web UI)
3. Admin → 知识库 → "全部重索引"
4. 等完成(看 `indexedAt` 更新)

期间 RAG 走 FULLTEXT 降级,不影响对话。

## 监控指标

### 核心指标(`MetricNames` 常量)

实际暴露的 metric(命名为 `rudder_ai_*`,以 `MetricNames` 为准):

| metric | 含义 | tags |
|--------|------|------|
| `rudder_ai_turn_total` | turn 启动累计 | mode, workspace, status(DONE/CANCELLED/FAILED) |
| `rudder_ai_turn_duration_seconds` | turn 耗时 | mode |
| `rudder_ai_tokens_total` | provider 调用 token 累计 | type(prompt/completion), model |
| `rudder_ai_cost_cents_total` | provider 调用成本(分) | provider, model, workspace |
| `rudder_ai_tool_call_total` | tool 调用计数 | tool, source(NATIVE/SKILL/MCP), success |
| `rudder_ai_tool_duration_seconds` | tool 调用耗时 | tool |
| `rudder_ai_provider_error_total` | provider 异常 | provider, reason |
| `rudder_ai_cancel_total` | 取消次数 | reason(user/timeout) |

Prometheus scrape `/actuator/prometheus`。

### 告警建议

| 告警 | 阈值 |
|------|------|
| turn 失败率(`rudder_ai_turn_total{status=FAILED}` 占比) | 连续 10min > 10% |
| turn 耗时 p99 | > 60s 持续 5min |
| Provider 异常突增 | `rudder_ai_provider_error_total` rate > 1/s |
| Qdrant 健康 | `isHealthy=false` 持续 30s |
| 元数据同步失败 | `lastSyncStatus=FAILED` 连续 3 次 |

## 数据库维护

### 归档老数据

长期运行后可能表体积很大:

| 表 | 策略 |
|----|------|
| `t_r_ai_message` | 按 `created_at < 90d` 归档到冷库 / 删 |
| `t_r_ai_session` | 同上,但保留 title 做会话回顾 |
| `t_r_ai_eval_run` | 按 batch 保留最近 N 批 |
| `t_r_ai_feedback` | 长期保留(做产品分析) |
| `t_r_ai_document_embedding` | 跟 `t_r_ai_document` 一起 sweep |

**没现成归档脚本**,需按业务规模写定时任务。

### 备份

- MySQL 全库备份:按公司 DBA 流程
- Qdrant:`POST /collections/{name}/snapshots`
- Redis:持久化(RDB + AOF)由运维配

## 升级迁移

### 升级 Rudder 版本

1. 备份 MySQL + Qdrant
2. 看 changelog 有没有 schema 迁移步骤
3. 滚动升级多节点(一台一台重启)
4. 升完后点管理后台几个页面冒烟:provider 测试连接 + 跑 eval 一批

### 切 LLM provider 不中断

不用停机。Admin → AI 配置 → AI 大模型 → 保存新配置 → `LlmPluginManager.updateActiveClient()` 原子切换 → 下一个 turn 走新 provider。进行中的 turn 继续走旧 client 直到结束。

### 切 Vector provider

需要重建 collection(Qdrant ↔ pgvector 数据不互通),参考 [换 Embedding 模型](#换-embedding-模型) 流程。

## 清理测试数据

开发 / 测试环境常见需求:

```sql
-- 删所有 eval run(保留 case 定义)
TRUNCATE t_r_ai_eval_run;

-- 删所有会话(保留 skill / config)
TRUNCATE t_r_ai_message;
TRUNCATE t_r_ai_session;

-- 删某数据源的所有 SCHEMA 文档
UPDATE t_r_ai_document SET deleted_at = NOW()
 WHERE doc_type = 'SCHEMA' AND source_ref LIKE 'metadata:MY_DS:%';
-- 然后手动触发 sweep 清 Qdrant
```

生产慎用。`TRUNCATE` 不走 soft-delete,Qdrant 会留孤儿,必须配合 sweep 或 collection 重建。

## 相关文档

- [故障排查](troubleshooting.md):问题现象 → 排查步骤
- [架构深入](architecture.md):一致性保障设计
