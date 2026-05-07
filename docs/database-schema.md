# 数据库 Schema

> Rudder 使用 MySQL 8.x 作为唯一持久化主库，共 **42 张 `t_r_*` 表**。本章按业务域分组，标注每张表的关键字段、关联、生命周期。表结构权威来源是 [`rudder-dao/src/main/resources/sql/schema.sql`](../rudder-dao/src/main/resources/sql/schema.sql)。

## 通用列

`io.github.zzih.rudder.common.entity.BaseEntity` 注入：

| 列 | 类型 | 含义 | 填充时机 |
|:---|:---|:---|:---|
| `id` | BIGINT AUTO_INCREMENT | 主键 | DB |
| `created_by` | BIGINT | 创建人 user.id | INSERT，由 MyBatis-Plus filler 注入 |
| `created_at` | DATETIME | 创建时间 | INSERT，DB DEFAULT |
| `updated_by` | BIGINT | 更新人 user.id | INSERT + UPDATE |
| `updated_at` | DATETIME | 更新时间 | INSERT + UPDATE，DB ON UPDATE 自动 |

业务标识：部分实体（`workflow_definition` / `task_definition` / `script` 等）额外有 `code BIGINT`（雪花 ID），用于跨版本稳定引用——`id` 因 schema 重导可能漂移，`code` 由代码生成器在创建时分配。

## 软删除（少数）

| 表 | 字段 | 行为 |
|:---|:---|:---|
| `t_r_ai_session` | `deleted_at` | UI「删除会话」标 deleted_at |
| `t_r_ai_document` | `deleted_at` | RAG 文档下线，向量同步从 Vector Store 清掉 |

**其它所有表都是硬删**。`workflow_definition` / `task_definition` 等没有软删，删除前请先确认无运行中实例。

## 域分组

### 平台基础（5）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_workspace` | `name`（unique） | 工作空间根 |
| `t_r_project` | `code`（unique 雪花 ID）/ `workspace_id` / `name` / `params` | Project 隶属于 Workspace；params 是项目级参数 JSON |
| `t_r_user` | `username`（unique）/ `password`（BCrypt）/ `is_super_admin` / `sso_provider` / `sso_id` | SSO 用户 password 为空 |
| `t_r_workspace_member` | `(workspace_id, user_id)` unique / `role` | role 字符串：`WORKSPACE_OWNER` / `DEVELOPER` / `VIEWER`（见 [权限模型](permissions.md)） |
| `t_r_service_registry` | `host` / `http_port` / `rpc_port` / `type`（SERVER/EXECUTION）/ `status` / `last_heartbeat_at` / `node_id` | 心跳 10s，超时 30s 标 OFFLINE；运维表物理删除 |

### 数据源（2）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_datasource` | `name`（unique，**不可变**）/ `datasource_type` / `host` / `port` / `database_name` / `params`（JSON）/ `credential`（密文 JSON） | 全局资源池，凭证 AES-CBC 加密，密钥来自 `RUDDER_ENCRYPT_KEY` |
| `t_r_datasource_permission` | `datasource_id` / `workspace_id` | 多对多授权关系 |

详见 [数据源](datasource.md)。

### 脚本与任务（4）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_script_dir` | `workspace_id` / `parent_id` / `name` / `path` | IDE 脚本树目录 |
| `t_r_script` | `workspace_id` / `dir_id` / `name` / `script_type`（HIVE_SQL / PYTHON / ...）/ `content` / `data_source_id` | 用户编辑的脚本 |
| `t_r_task_definition` | `code`（unique 雪花 ID）/ `workspace_id` / `project_code` / `task_type` / `params_json` / `data_source_id` / `retry_times` / `retry_interval` / `timeout` | 工作流节点的任务配置 |
| `t_r_task_instance` | `task_definition_code` / `workflow_instance_id` / `status` / `start_time` / `end_time` / `log_path` / `result_path` / `var_pool` | 每次运行新增一行；`status`：PENDING / RUNNING / SUCCESS / FAILED / KILLED |

> `task_instance` 既给工作流节点用，也给 IDE 直接执行 SQL 用，统一一张表。

### 工作流（4）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_workflow_definition` | `code`（unique）/ `workspace_id` / `project_code` / `name` / `dag_json` / `global_params` / `published_version_id` | DAG 定义 + 全局参数 |
| `t_r_workflow_schedule` | `workflow_definition_code` / `cron_expression` / `start_time` / `end_time` / `timezone` / `status`（ONLINE/OFFLINE） | Cron 调度，由内置 Quartz 触发 |
| `t_r_workflow_instance` | `workflow_definition_code` / `version_id` / `status` / `start_time` / `end_time` / `var_pool` | 每次触发新增 |
| `t_r_publish_record` | `publish_type`（WORKFLOW/PROJECT）/ `target_code` / `version_id` / `status`（PENDING_APPROVAL/PUBLISHED/PUBLISH_FAILED）/ `error_message` | 审计表，**不可删除** |

详见 [工作流](workflow.md)。

### 版本与审批（3）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_version_record` | `entity_type`（WORKFLOW/SCRIPT）/ `entity_code` / `version_no` / `snapshot_json` / `comment` | 通用版本快照 |
| `t_r_approval_record` | `target_type` / `target_id` / `approver_id` / `status` / `comment` | 审批记录（飞书 / Slack / KissFlow 回调） |
| `t_r_audit_log` | `user_id` / `username` / `module` / `action` / `target` / `ip` / `user_agent` / `payload` | 全操作审计；只追加，不删除 |

### 平台 SPI 配置（9）

每张表都对应一类 SPI 的当前配置，单例为主，部分支持多实例：

| 表 | 单例 / 多实例 | 备注 |
|:---|:---|:---|
| `t_r_approval_config` | 单例（per type） | 审批渠道 |
| `t_r_metadata_config` | 单例 | DataHub / OpenMetadata / JDBC |
| `t_r_runtime_config` | 单例 | LOCAL / ALIYUN / AWS |
| `t_r_version_config` | 单例 | LOCAL（MySQL） / GIT |
| `t_r_file_config` | 单例 | LOCAL / HDFS / OSS / S3 |
| `t_r_result_config` | 单例 | json / csv / parquet / orc / avro 序列化默认 |
| `t_r_notification_config` | 平台级 + 工作空间级（多实例） | 飞书 / 钉钉 / Slack |
| `t_r_ai_config` | 单例 per type（LLM / EMBEDDING / VECTOR） | 三类 AI provider 共表，按 type 区分 |

> 这些表都通过管理后台 UI 写入，热生效（详见 [配置参考](configuration.md#十五spi-provider-配置不在这里)）。

### AI 业务（15）

| 分组 | 表 | 关键字段 |
|:---|:---|:---|
| 会话 | `t_r_ai_session` | `workspace_id` / `mode`（CHAT/AGENT）/ `model_snapshot` / `system_prompt_override` / `total_*_tokens` / `total_cost_cents` / `deleted_at` |
| 消息 | `t_r_ai_message` | `session_id` / `role`（USER/ASSISTANT/TOOL_CALL/TOOL_RESULT）/ `content` / `status`（PENDING/STREAMING/DONE/CANCELLED/FAILED）/ `prompt_tokens` / `completion_tokens` |
| 反馈 | `t_r_ai_feedback` | `message_id` / `polarity`（UP/DOWN）/ `comment` |
| Skill | `t_r_ai_skill` | `code` / `name` / `description` / `prompt_template` / `tool_config_json` |
| Pinned 表 | `t_r_ai_pinned_table` | `workspace_id` / `user_id` / `data_source_id` / `database` / `table` |
| 上下文策略 | `t_r_ai_context_profile` | `scope`（GLOBAL/WORKSPACE/USER）/ `inject_*` 系列 / `max_schema_tables` |
| 方言覆盖 | `t_r_ai_dialect` | `engine_type` / `prompt_slot` |
| 工具配置 | `t_r_ai_tool_config` | `tool_name` / `tool_kind`（BUILTIN/MCP/SKILL）/ `workspace_ids`（JSON）/ `permission_rule` |
| MCP server | `t_r_ai_mcp_server` | `name` / `transport`（STDIO/HTTP_SSE）/ `command` / `args` / `env` / `headers` / `enabled` |
| 元数据同步配置 | `t_r_ai_metadata_sync_config` | `data_source_id` / `cron` / `last_run_at` / `enabled` |
| 文档原文 | `t_r_ai_document` | `workspace_ids`（JSON）/ `doc_type`（WIKI/SCRIPT/SCHEMA/METRIC_DEF/RUNBOOK）/ `engine_type` / `source_ref` / `title` / `content` / `content_hash` / `indexed_at` / `deleted_at`；带 `FULLTEXT (title, content) WITH PARSER ngram` |
| 向量索引 | `t_r_ai_document_embedding` | `document_id` / `chunk_idx` / `qdrant_point_id` / `dim`；向量本身存在 Vector Store |
| 评测用例 | `t_r_ai_eval_case` | `mode`（CHAT/AGENT）/ `data_source_id` / `prompt` / `expected_json` / `context_json` |
| 评测结果 | `t_r_ai_eval_run` | `case_id` / `status` / `tool_calls_json` / `fail_reasons_json` / `tokens` / `latency_ms` |

详见 [AI 模块](ai/README.md)。

### 脱敏（2）

| 表 | 关键字段 | 备注 |
|:---|:---|:---|
| `t_r_redaction_strategy` | `code`（稳定编码）/ `executor_type`（REGEX_REPLACE/PARTIAL/REPLACE/HASH/REMOVE）/ `match_regex` / `replacement` / `keep_prefix` / `keep_suffix` / `mask_char` / `replace_value` / `hash_length` | 「怎么脱」 |
| `t_r_redaction_rule` | `name` / `type`（TAG/COLUMN/TEXT）/ `pattern` / `strategy_code` / `priority` / `enabled` | 「匹配什么」 |

详见 [数据脱敏](redaction.md)。

## 表关系（核心）

```
workspace ─┬─< workspace_member >─ user
           │
           ├─< project ─< workflow_definition ─┬─< workflow_schedule
           │                                   ├─< workflow_instance ─< task_instance
           │                                   ├─< task_definition (引用 datasource)
           │                                   └─< publish_record
           │
           ├─< script_dir ─< script (引用 datasource)
           │
           ├─< datasource_permission >─ datasource ──< t_r_ai_pinned_table
           │
           └─< t_r_ai_session ─< t_r_ai_message ─< t_r_ai_feedback

datasource ──< t_r_ai_metadata_sync_config

t_r_ai_document ─< t_r_ai_document_embedding   （向量本身在 Qdrant / pgvector）

t_r_ai_skill / t_r_ai_mcp_server ─── 由 t_r_ai_tool_config 控制 workspace 可见性

t_r_redaction_rule ──> t_r_redaction_strategy（按 strategy_code 查找）

t_r_service_registry / t_r_audit_log / t_r_publish_record / t_r_version_record  （独立表，不强关联）
```

## 索引策略

- **主键** 全部 `BIGINT AUTO_INCREMENT`
- **唯一键**：业务标识（`workspace.name`、`datasource.name`、`workflow_definition.code`、`task_definition.code`、`user.username`、`user.(sso_provider, sso_id)`）
- **频繁查询**：
  - `task_instance` 按 `(workflow_instance_id, status)` 索引
  - `audit_log` 按 `(user_id, module, action, created_at)`
  - `service_registry` 按 `(type, status, last_heartbeat_at)`
  - AI 相关表按 `(workspace_id, deleted_at, created_at)`
- **JSON 字段**（`workflow_definition.dag_json` / `task_definition.params_json` / `datasource.params` / `ai_*.workspace_ids` / `version_record.snapshot_json`）：MySQL 8 原生 JSON 类型，支持 `JSON_EXTRACT` 但不建索引
- **FULLTEXT**：`t_r_ai_document(title, content)` ngram 解析器，作为 RAG 降级检索

## Schema 演进

启动时由 Spring Boot `spring.sql.init` 自动加载 `schema.sql` + `data.sql`：

- `IF NOT EXISTS` + `continue-on-error: true` → 重启幂等
- **新增列**：直接改 `schema.sql`，旧库执行需要 DBA 手动 `ALTER TABLE`
- **删除列 / 重命名**：必须走维护窗口 + 离线迁移
- 当前阶段（pre-1.0）按 [反向兼容禁用](../README.md) 原则**直接硬切**，不做双读双写

生产部署见 [部署指南 · 升级](deployment.md#五升级)。

## 数据量预估

| 表 | 写入频率 | 大小预估 | 清理建议 |
|:---|:---|:---|:---|
| `task_instance` | 每次任务 1 行 | 高（每天可达万行） | 按月分区或归档 |
| `workflow_instance` | 每次工作流 1 行 | 中 | 同上 |
| `audit_log` | 每次写操作 1 行 | 高 | 归档到对象存储 |
| `service_registry` | 心跳 10s 一次 | 极小（活跃节点数） | 不需要清理 |
| `t_r_ai_message` | 每条 AI 消息 1 行 | 中（带 token 计数） | 按 session 整体软删 |
| `t_r_ai_document_embedding` | 每文档 chunk 一行 | 与文档量同量级 | RAG 重索引时整体清空 |

> 没有内置归档机制；高流量部署需要自己写清理 / 归档脚本，按需挪到归档表 / S3 Parquet。

## 备份建议

- **每日 mysqldump**：全表，至少保留 7 天 + 月度归档
- **关键表 binlog 同步**：`t_r_workflow_definition` / `t_r_task_definition` / `t_r_datasource` / `t_r_*_config`，准实时同步到备份库
- **结果文件**：`task_instance.result_path` 指向 FileStorage，备份策略由 FileStorage 自身（HDFS replication / S3 versioning）保证
- **Vector Store**：Qdrant / pgvector 单独备份；`t_r_ai_document_embedding.qdrant_point_id` 是双方对账钥匙，丢失时可重新索引

## 排障速查

| 症状 | 查这张表 |
|:---|:---|
| 工作流不调度 | `t_r_workflow_schedule.status` / `start_time` / `end_time` |
| 节点没派发 | `t_r_service_registry.status` 是否有 ONLINE Execution |
| 任务执行失败 | `t_r_task_instance.log_path` / `error_message` |
| 数据源无法连接 | `t_r_datasource_permission`（workspace 是否授权）+ `t_r_datasource.credential`（密钥是否变更） |
| AI 无法对话 | `t_r_ai_config` type=LLM 是否有 enabled 行 |
| RAG 召回为空 | `t_r_ai_document.indexed_at` 是否为 null（未向量化） |
| 用户登录失败 | `t_r_user`（SSO 走 `sso_provider + sso_id`） |
| 用户没权限 | `t_r_workspace_member.role` |

## 相关文档

- [架构总览](architecture.md) — 整体模块依赖
- [工作流](workflow.md) / [任务类型](task-types.md) / [数据源](datasource.md) / [权限模型](permissions.md) / [AI 模块](ai/README.md) — 各域专题
- [`schema.sql`](../rudder-dao/src/main/resources/sql/schema.sql) — 表结构定义
- [`data.sql`](../rudder-dao/src/main/resources/sql/data.sql) — 种子数据
