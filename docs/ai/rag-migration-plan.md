# RAG 迁移到 Spring AI 2.0 Modular RAG (已完成)

> 评估时间: 2026-05-06
> 完成时间: 2026-05-07
> 状态: ✅ **已完成** — Phase 1 / 2 / 3 全部上线 (Phase 2 默认 disabled,经 yml 显式启用)
> 参考文档: https://docs.spring.io/spring-ai/reference/2.0/api/retrieval-augmented-generation.html

## 现状

Rudder 当前 RAG 是**自研 Advisor + 自研 Retriever**:

```
ChatClient.advisors(RudderRagAdvisor)
    ↓ before():
        DocumentRetrievalService.retrieve(workspaceId, docType, query, engineFilter, topK)
        手写 PromptTemplate 拼到 system message
    ↓ LLM
```

只做了 **Retrieval + Augment 两个阶段**, 缺 Spring AI 2.0 的另两阶段(Pre-Retrieval Query Transform / Post-Retrieval Rerank)。

## 目标架构

接入 Spring AI `RetrievalAugmentationAdvisor` 4 阶段:

```
RetrievalAugmentationAdvisor
    ├ Pre-Retrieval
    │   ├ RewriteQueryTransformer  (LLM 重写用户 query 提升召回)
    │   └ MultiQueryExpander       (1 → N 个变体, 复杂问题多角度查)
    ├ Retrieval
    │   ├ RudderDocumentRetriever  (自研, 包装 DocumentRetrievalService)
    │   └ ConcatenationDocumentJoiner  (合并多变体结果 + 去重)
    ├ Post-Retrieval
    │   └ DocumentPostProcessor    (接入现有 rerank-api SPI)
    └ Generation
        └ ContextualQueryAugmenter (替代手写 PromptTemplate, 支持 allowEmptyContext)
```

## 必须保留 (Spring AI 不直接支持)

| 能力 | 原因 |
|:---|:---|
| `rudder-vector-api` SPI + 多实现 | Spring AI 的 `VectorStore` 是启动期 bean, 不支持运行时切 (Rudder 管理后台动态配 Qdrant ↔ Pgvector ↔ Local) |
| `rudder-embedding-api` SPI | 同上 |
| `DocumentRetrievalService` 内部 Vector + FULLTEXT 兜底 | 没配 Vector 时优雅降级到 MySQL FULLTEXT, Spring AI 框架不内建 |
| Workspace ∪ shared 后置 OR 过滤 | Spring AI `FilterExpression` 表达不了 `workspaceIds IS NULL OR workspaceIds CONTAINS x` |

## 改造任务清单

### Phase 1: 适配层 (必须)

- [ ] 加 `spring-ai-rag` 依赖 (rudder-ai/pom.xml,已加)
- [ ] 写 `RudderDocumentRetriever implements org.springframework.ai.rag.retrieval.search.DocumentRetriever`
  - 输入: `Query` (含 text + context map)
  - 内部: 从 context 拿 `workspaceId / docType / engineTypes / topK`, 调 `DocumentRetrievalService.retrieve`
  - 输出: `List<org.springframework.ai.document.Document>` (把 `RetrievedChunk` 转成 Spring AI Document)
- [ ] 删 `RudderRagAdvisor` (整个文件)
- [ ] `EvalExecutor` / `AgentExecutor` 改造:
  - 不再 `chatClient.advisors(rudderRagAdvisor)` + `spec.param(CTX_*, ...)`
  - 改为 `chatClient.advisors(RetrievalAugmentationAdvisor.builder()....build())`,context 通过 `Query.context` 或 `Supplier<FilterExpression>` 注入

### Phase 2: 增强 (强烈建议)

- [ ] 加 `RewriteQueryTransformer` — 让 LLM 改写用户 query (例: "把 sql 跑慢的问题列一下" → "MySQL 慢查询排查方法 + 性能调优 + 慢日志分析")
  - 配置: 复用当前 LLM provider 的 ChatModel
- [ ] 加 `MultiQueryExpander` — 1 query → N 个变体并行检索
  - 阈值: 控制 N 不超 3 (避免 token 成本爆炸)
- [ ] 加 `ConcatenationDocumentJoiner` — 多变体结果合并去重 (按 documentId)
- [ ] 接入 rerank — 写 `DocumentPostProcessor` 包装 `RerankClient` SPI (现有 rerank-api 模块没接入 RAG 链)

### Phase 3: 用 ContextualQueryAugmenter 替代手写 prompt

- [ ] 删除当前 `RAG_TEMPLATE` 常量
- [ ] 用 `ContextualQueryAugmenter.builder().allowEmptyContext(true).build()` 替代
- [ ] 测试: 没检索到文档时, advisor 行为是否合理 (期望: LLM 仍能基于 user query 回答)

## 工作量估算

- Phase 1 (适配层): ~3 小时
  - RudderDocumentRetriever: 60 行
  - 调用方迁移 (EvalExecutor / AgentExecutor): 改 2 处, 每处 ~15 行
  - 测试调整
- Phase 2 (增强): ~4 小时
  - QueryTransformer / Expander 配置: 简单 (Spring AI 现成组件)
  - rerank DocumentPostProcessor: 30 行
- Phase 3 (Augmenter): ~1 小时

**总计: ~8 小时**

## 风险

1. **Spring AI 2.0-M5 是 milestone**, RAG API 可能继续变化(M5 → M6 → GA)
2. **QueryTransformer 增加 LLM 调用次数** (rewrite 一次 + 实际回答一次), 注意成本
3. **MultiQueryExpander 同样放大 token 消耗** (N 个并行检索 × 每个 topK 文档 → context 膨胀)
4. **ContextualQueryAugmenter 的 prompt 模板**与 Rudder 当前的中文 / 系统说明风格可能不一致, 需要 customize template

## 触发条件

- MCP 功能完成且稳定
- Spring AI 2.0 走到 RC 或 GA (减少 API 漂移风险)
- 业务方反馈当前 RAG 召回质量不足 (现在没强需求驱动)

## 相关代码文件 (改造时定位)

| 当前 | 改造后 |
|:---|:---|
| `rudder-ai/.../orchestrator/advisor/RudderRagAdvisor.java` | 删除 |
| `rudder-ai/.../rag/DocumentRetrievalService.java` | 保留, 被 RudderDocumentRetriever 包装 |
| `rudder-ai/.../eval/EvalExecutor.java:99-114` | 调用方迁移到 RetrievalAugmentationAdvisor |
| `rudder-ai/.../orchestrator/AgentExecutor.java:159-175` | 同上 |
| `rudder-spi/rudder-rerank/*` | 接入 DocumentPostProcessor (新桥接类) |

---

## 已做的预备工作 (2026-05-06)

- ✅ 评估完成,确认 `RetrievalAugmentationAdvisor` 是合适的目标架构
- ✅ 确认必须保留的 Rudder 自研能力 (运行时切 VectorStore / FULLTEXT 兜底 / OR 过滤)
- ✅ `rudder-ai/pom.xml` 已加 `spring-ai-rag` 依赖
- ⚠️ 未删任何代码, 未改 Advisor, 当前 RAG 链路保持原样工作

## 实际落地 (2026-05-07)

### Phase 1: 适配层 ✅
- 加 `spring-ai-rag` 依赖 (`rudder-ai/pom.xml`)
- 新建 `RudderDocumentRetriever implements DocumentRetriever`,包装 `DocumentRetrievalService`
- `ChatClientFactory` 改用 `RetrievalAugmentationAdvisor.builder()`,order 保持 `HIGHEST_PRECEDENCE+1000`
- 删除 `RudderRagAdvisor.java` (整个文件)
- 3 处调用方迁移: `EvalExecutor` / `AgentExecutor` / `TurnExecutor` —— `RudderRagAdvisor.CTX_*` → `RudderDocumentRetriever.CTX_*`
- 4 个 retriever 单元测试覆盖关键路径 (workspaceId 缺失 / Integer→Long / 异常降级 / 元数据透传)

### Phase 2: Pre/Post-Retrieval 增强 ✅ (默认 disabled)
- 新建 `RagProperties` (`@ConfigurationProperties("rudder.ai.rag")`)
- `ChatClientFactory.buildRagAdvisor()` 按开关装配 `RewriteQueryTransformer` / `MultiQueryExpander`
- **防递归**: rewrite/expand 用独立 vanilla `ChatClient.builder(chatModel)`,**不挂任何 advisor**
- DocumentJoiner 用 Spring AI 默认 `ConcatenationDocumentJoiner` (合并 + 去重)
- ⚠️ rerank `DocumentPostProcessor` 跳过 —— `rudder-rerank` SPI 模块尚未创建,后续单独立项
- `application.yml` 加默认配置 (rewrite/multi-query 默认 false,显式 env 启用)

### Phase 3: ContextualQueryAugmenter ✅
- `RAG_TEMPLATE` 常量随 `RudderRagAdvisor` 一并删除
- `ContextualQueryAugmenter.builder().allowEmptyContext(true)` 接入,保持"无文档时仍回答"行为
- `allowEmptyContext` 经 `rudder.ai.rag.augmenter.allow-empty-context` 可配置

### 关键防 bug 决策
1. 保留自研 `RudderDocumentRetriever`(不用 Spring AI 内置 `VectorStoreDocumentRetriever`),理由:
   - 运行时切 VectorStore (Admin 后台动态配 Qdrant ↔ Pgvector ↔ Local)
   - FULLTEXT 兜底 (无 Vector 配置时降级)
   - workspace `IS NULL OR CONTAINS x` OR 过滤
2. `RetrievalAugmentationAdvisor implements BaseAdvisor` —— 已确认支持 `.stream()`,
   retrieval 在 `BaseAdvisor.DEFAULT_SCHEDULER (boundedElastic)` 上同步跑一次,不阻塞 reactor netty
3. CTX 常量从旧 advisor 迁移到 retriever,key 字符串保持不变 (`rudder.rag.workspaceId` 等),向前兼容
4. workspaceId 缺省 / retrieval 异常 → 返回空 list (LLM 退化为无 RAG 模式),不阻断主对话
