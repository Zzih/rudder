# 知识库 & RAG

> AI 能否生成**对的 SQL / 脚本**,核心靠知识库里有没有**你数据源的表结构**。这份文档讲清楚:文档怎么来、怎么索引、怎么被检索。

## 什么是知识库

MySQL 表 `t_r_ai_document`,每行是一篇 AI 可以检索到的文档。

**文档类型(docType)**:

| docType | 来源 | 范围 |
|---------|------|------|
| **SCHEMA** | 元数据自动同步 | **平台共享**(`workspace_id=NULL`,所有 workspace 共用) |
| **WIKI** | 管理员手工创建 / 上传 | workspace 专属 或 平台共享 |
| **RUNBOOK** | 管理员手工 | 运维手册、故障处理步骤 |
| **METRIC_DEF** | 管理员手工 | 指标定义(DAU / 复购率 等) |
| **SCRIPT** | 管理员手工 | 参考脚本 / best practice |

## 文档入库两条路

### A. 元数据自动同步(推荐,解决"AI 不知道我有哪些表"的问题)

**配置入口**:Admin → AI 配置 → 知识库 → "表结构同步"区域。

后端端点:`GET / POST / PUT / DELETE /api/ai/metadata-sync`,手工触发 `POST /api/ai/metadata-sync/sync/{datasourceId}`。

每个数据源一条同步配置(表 `t_r_ai_metadata_sync_config`),字段:

| 字段 | 说明 |
|------|------|
| **数据源** | 必填,同一 datasource 只能一条配置 |
| **定时同步** | 开关 + cron 表达式(Spring cron,秒-周)。关闭 = 仅手动触发 |
| **同步范围** | 三级白名单:Catalog(3 层引擎) / Database / Table。每级可选"全部"或"指定" |
| **排除关键字** | 分号分隔,表名**包含任一关键字**就跳过(不区分大小写,不支持通配符)。常用:`tmp_;_bak;test` |
| **每表最大列数** | 超出截断,默认 50。防超大宽表撑爆文档大小 |
| **跨引擎访问路径** | 可选 JSON,用于同一份 Hive 元数据被 Trino/StarRocks 共享时的 catalog 前缀映射 |

同步逻辑(`MetadataSyncService`):
1. `listCatalogs / listDatabases / listTables / getTableDetail` 按当前 metadata provider(JDBC / DataHub / OpenMetadata)拉
2. 每张表生成一份 markdown(含列名 / 类型 / nullable / PK / 注释 / 跨引擎访问路径)
3. `sourceRef = metadata:{ds}:{catalog?.db.table}` 作为唯一 key,SHA256 内容哈希判重
4. 第一次 INSERT,之后有变更 UPDATE,无变更 SKIP
5. 源头删掉的表 → soft-delete 文档 + 清 Qdrant 向量点
6. 末尾做 orphan sweep:扫 `t_r_ai_document_embedding` 找 AiDocument 已不存在的记录,统一清理

**同步开始前**会先调 `metadataClient.invalidateCache(dsName)` 清 Redis 元数据缓存,保证读到最新 DDL。

### B. 手工文档

**Admin → AI 配置 → 知识库 → "文档"区域**(`AiDocumentController`,`/api/ai/documents`)。

三种录入方式:
1. **新建**(`POST /api/ai/documents`):填 title / docType / engineType(SCHEMA 类必填) / content,markdown
2. **上传文件**(`POST /api/ai/documents/upload`):PDF / DOCX / MD / HTML / TXT 等,走 Tika 解析
3. **重索引**(`POST /api/ai/documents/reindex`):换 embedding 模型后批量重生成

文档生命周期:`create → index → (update) → (soft-delete + clear vectors)`。检索端点:`GET /api/ai/documents/search`。

## RAG 检索链路

**触发**:`AiContextProfile.injectWikiRag = true`(workspace 或 session 级可配),chat/agent 两种模式都会触发。

```
用户问题
  → RudderRagAdvisor.before()
  → DocumentRetrievalService.retrieve(workspaceId, docType?, query, engineTypes?, topK)
      │
      ├─ 语义检索(配了 Vector + Embedding)
      │    └─ 对 collection `{wsId}_{docType}` 做 topK 相似度
      │
      └─ 降级(未配 Vector 或语义结果空)
           └─ 走 MySQL FULLTEXT(title + content match against query)
  → 把 top-k chunk 拼成 "## Knowledge base" 段落追加到 system prompt
```

### 跨引擎可见性过滤

**关键机制**:SCHEMA 文档入库时带 `engineType`(MYSQL / HIVE / TRINO / ...)。检索时按当前脚本 TaskType → `allowedEngines` 过滤(由 `EngineCompatibility` 维护映射)。

例子:用户在 StarRocks SQL 脚本里问问题 → 只召回 engineType ∈ {STARROCKS, HIVE(共享)} 的 SCHEMA,**不**召回 MySQL 的表。防止 LLM 引用别家引擎的表名。

### 平台共享 vs workspace 专属

| docType | workspaceId | 谁能看到 |
|---------|-------------|---------|
| SCHEMA | NULL(同步产出) | 所有 workspace 共用,collection `0_SCHEMA` |
| WIKI / RUNBOOK / ... | NULL | 所有 workspace 共用 |
| 同上 | 某个 wsId | 仅该 workspace 可见 |

**retrievalService 查询时**按 docType 自动映射 effective workspaceId:SCHEMA 类型强制 NULL,其他尊重传入。

## 向量化 & 一致性保障

### 写入链路

```
DocumentIngestionService.upsertBySourceRef(doc)
  │
  ├─ contentHash 相同 → SKIP
  ├─ 新增 → INSERT doc + tryIndex(生成 embedding → 写 Qdrant → 写 embedding 映射表 → 标 indexedAt)
  └─ 变更 → deleteVectors(旧) + UPDATE doc + tryIndex(新)
```

### `tryIndex` 双写补偿

`vectorStore.upsert()` 成功但 embedding 映射表 `insertBatch()` 失败时,自动 `deleteByIds` 回滚 Qdrant,避免孤儿点。

### Sweep(孤儿清理)

**每次元数据同步结束**自动跑 `DocumentIngestionService.sweepOrphanEmbeddings(2000)`:
- SQL 扫 `t_r_ai_document_embedding LEFT JOIN t_r_ai_document` 找 doc 已 soft-delete/物理删除的行
- 批量 `deleteByIds` 清 Qdrant 对应 point
- 删 embedding 映射表行

**手工触发**(换 embedding 模型后):Admin → 知识库 → "全部重索引"。

## 元数据 Provider

`MetadataClient` SPI(`rudder-spi/rudder-metadata/`)有三个实现:

### JDBC(默认,`rudder-metadata-jdbc`)

直连数据源连接池,`DatabaseMetaData.getTables / getColumns / getPrimaryKeys` 拉。优点:零依赖;缺点:每次实时查,大库可能慢。

**缓存**:Redis,TTL 5min。跨节点共享(多节点部署时一致)。`invalidateCache(dsName)` 手工或同步开始时触发。

### DataHub(`rudder-metadata-datahub`)

需要 DataHub 侧配 `platform_instance` 才能做数据源级精确匹配。详见 [platform_instance 约定](#datahub-platform_instance-约定)。

**降级模式**:DataHub 里没有对应 `dataPlatformInstance` 实体时,自动退回"平台级视图"(返回该 platform 下所有 dataset)。

### OpenMetadata(`rudder-metadata-openmetadata`)

约定:**Rudder 数据源 name == OpenMetadata DatabaseService name**。`listCatalogs / listDatabases / listTables` 按 service FQN 查。

3 层引擎(Trino / StarRocks)映射:
- Rudder `catalog` = OM `Database`
- Rudder `database` = OM `DatabaseSchema`

2 层引擎:
- Rudder `database` = OM `Database`
- OM `DatabaseSchema` 层自动取第一个

### DataHub platform_instance 约定

**设置**:在 DataHub ingestion recipe 的 `source.config` 下配:

```yaml
source:
  type: hive
  config:
    platform_instance: <Rudder 里该数据源的 name>   # ← 关键
    host_port: 10.0.0.1:10000
    ...
```

**生效**:URN 从 `(hive, db.table, PROD)` 变成 `(hive, <dsName>.db.table, PROD)`,Rudder 用 `platformInstance` GraphQL filter 精确过滤。

**Rudder 侧约束**:`Datasource.name` 创建后**不可修改**(改了就脱钩)。UI 编辑时 name 字段 disabled。

## 检索参数调优

`AiContextProfile` 控制是否触发 RAG(`injectWikiRag`)。topK / docType 过滤目前由代码默认 + retrieve 调用方传入,不在 profile 表持久化。回答质量差时优先看:
- 文档是否成功向量化(`indexedAt` 是否非空)
- engineType 过滤是否过严
- embedding 模型是否合中文(必要时换 `bge-m3` 类)

## 相关文档

- [Provider 配置](providers.md):配 Vector / Embedding
- [运维日常](operations.md):重索引 / 孤儿清理操作
- [故障排查](troubleshooting.md#rag-召回差):RAG 调试

## 相关数据表

- `t_r_ai_document` — 原文
- `t_r_ai_document_embedding` — 向量映射(doc ↔ Qdrant pointId)
- `t_r_ai_metadata_sync_config` — 每数据源一条同步配置
- Qdrant collections `{wsId}_{docType}` — 向量存储(或 pgvector / FULLTEXT 降级)
