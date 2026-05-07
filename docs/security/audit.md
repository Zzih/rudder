# 审计日志

> Rudder 平台所有写类操作（登录、CRUD、发布、执行、配置变更等）由 `@AuditLog` 切面异步落到 `t_r_audit_log`，仅 `SUPER_ADMIN` 可查。本章覆盖切面机制、字段语义、扩展、归档。

## 数据模型

```sql
CREATE TABLE t_r_audit_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    username        VARCHAR(64),
    module          VARCHAR(32),
    action          VARCHAR(32),
    resource_type   VARCHAR(32),
    resource_code   BIGINT,
    description     VARCHAR(512),
    request_ip      VARCHAR(64),
    request_method  VARCHAR(16),
    request_uri     VARCHAR(256),
    request_params  TEXT,
    status          VARCHAR(16),       -- SUCCESS / FAILURE
    error_message   VARCHAR(512),
    duration_ms     BIGINT,
    created_at      DATETIME
)
```

> **只追加，不修改、不删除**。归档由 DBA 主导，详见下文。

## 切面机制

`io.github.zzih.rudder.common.audit.AuditLogAspect` 拦截所有打了 `@AuditLog` 的方法：

```java
@PostMapping("/datasources")
@AuditLog(
    module = AuditModule.DATASOURCE,
    action = AuditAction.CREATE,
    resourceType = AuditResourceType.DATASOURCE,
    resourceCode = "#result.data.id",     // SpEL，对返回值求 id
    description = "创建数据源"
)
public Result<Datasource> create(@RequestBody DatasourceRequest req) { ... }
```

执行流程：

```
1. proceed()                            业务方法执行
2. finally:
   ├ 取 UserContext（线程内当前用户）
   ├ 取 HttpServletRequest（IP / method / URI）
   ├ resolveResourceCode(SpEL)         对方法参数 / 返回值求值
   ├ AspectArgUtils.buildSafeArgsJson  入参序列化（剔密码 / token / 长 body）
   ├ AuditLogRecord.from(...)          组装记录
   └ AuditLogAsyncService.saveAuditLog @Async 落库
```

业务异常照常抛出，**审计日志写失败不影响主流程**（aspect catch 异常仅 WARN）。

## 异步管线

```
@AuditLog 方法
   │
   ↓ proceed
   ↓ finally
AuditLogAsyncService.saveAuditLog (@Async)
   │
   ↓ Spring task executor
AuditLogPersister.save → MyBatis insert into t_r_audit_log
```

线程池由 `spring.task.execution.pool.*` 控制（详见 [配置参考](../configuration.md#六异步线程池审计--async)）：

```yaml
core-size:      4         # RUDDER_ASYNC_CORE_SIZE
max-size:       16        # RUDDER_ASYNC_MAX_SIZE
queue-capacity: 1000      # RUDDER_ASYNC_QUEUE_CAPACITY
```

> `queue-capacity` 不设上限的话，DB 抖动时审计积压会爆 OOM。**宁可丢审计也不能吃爆内存**——队列满时新审计被 reject，记 WARN。高流量部署调大；低规格调小。

## 受控枚举

`module / action / resourceType` **强制走枚举**，禁止字面量字符串。新增前先在对应 enum 加项。

### `AuditModule`（22 项）

```
AI / AI_CONFIG / APPROVAL / APPROVAL_CONFIG / AUTH / DATASOURCE
EXECUTION / FILE / FILE_CONFIG / JOB / METADATA_CONFIG
NOTIFICATION_CONFIG / PROJECT / PUBLISH / RUNTIME_CONFIG
SCRIPT / SCRIPT_DIR / USER / VERSION_CONFIG
WORKFLOW / WORKFLOW_INSTANCE / WORKSPACE
```

### `AuditAction`（按域分组）

| 组 | 项 |
|:---|:---|
| 通用 CRUD | CREATE / UPDATE / DELETE |
| 生命周期 | EXECUTE / CANCEL / RUN / KILL |
| 成员 / 权限 | ADD_MEMBER / REMOVE_MEMBER / UPDATE_MEMBER_ROLE / UPDATE_OWNER |
| 认证 | LOGIN / LOGIN_LDAP |
| 版本 | COMMIT / ROLLBACK |
| 审批 | APPROVE / REJECT / CALLBACK |
| 发布 | EXECUTE_PUBLISH / PUBLISH_PROJECT / PUBLISH_WORKFLOW |
| AI | CHAT / AGENT_RUN / AGENT_STREAM / CREATE_SESSION / RENAME_SESSION / DELETE_SESSION / APPEND_MESSAGE |
| 文件 | UPLOAD / MKDIR / RENAME / ONLINE_CREATE / UPDATE_CONTENT |
| 数据源 | TEST_CONNECTION / REFRESH_META_CACHE |
| 任务 | TRIGGER_SAVEPOINT / PUSH / DISPATCH |
| 配置 / SPI | VALIDATE / TEST / SEND_TEST / UPDATE_PLATFORM_CONFIG / UPDATE_WORKSPACE_CONFIG / DELETE_WORKSPACE_CONFIG |
| 用户管理 | RESET_PASSWORD / TOGGLE_SUPER_ADMIN / UPDATE_EMAIL |

### `AuditResourceType`（15 项）

```
NONE                       默认，落库为空字符串（如 LOGIN）
AI_SESSION
APPROVAL_RECORD
DATASOURCE
FILE
NOTIFICATION_CONFIG
PROJECT
SCRIPT / SCRIPT_DIR
SPI_CONFIG                 跨多个 SPI 类型的统一资源类型
TASK_INSTANCE
USER
WORKFLOW_DEFINITION
WORKFLOW_INSTANCE
WORKSPACE
```

> `AuditResourceType` 故意不复用 `common.enums.ResourceType`（后者是 `VersionStore` 专用 SCRIPT/WORKFLOW，审计维度更细）。

## SpEL 求值 `resourceCode`

`@AuditLog(resourceCode = "<expr>")` 表达式由 Spring `CachedExpressionEvaluator` + `MethodBasedEvaluationContext` 求值，可用变量：

| 变量 | 含义 |
|:---|:---|
| `#paramName` | 按参数名（需要 `-parameters` 编译，Lombok 默认开） |
| `#a0`, `#a1`, ... `#aN` | 位置参数 |
| `#root.method` | 反射的 Method |
| `#root.args` | 全部参数数组 |
| `#result` | 业务方法**返回值**（异常时不可用，求值短路为 null） |

**结果必须可转 `Long`**。字符串 id 类资源当前不支持。

常见写法：

```java
resourceCode = "#id"                  // 路径变量直接是 Long
resourceCode = "#code"                // 业务标识雪花 ID
resourceCode = "#request.userId"      // 入参对象的字段
resourceCode = "#result.data.id"      // CREATE 取返回值
resourceCode = ""                     // 不记，非资源级动作（如 LOGIN）
```

求值失败（typo / 类型不匹配 / null 路径）→ `WARN` + `resource_code = null`，**不影响业务响应**。

## 入参序列化

`AspectArgUtils.buildSafeArgsJson` 把方法入参打成 JSON 写到 `request_params`：

- 跳过 `HttpServletRequest / HttpServletResponse / MultipartFile / Servlet*` 等不可序列化对象
- 跳过 `byte[] / InputStream`
- 截断超 `MAX_REQUEST_PARAMS_LEN = 2000` 字节
- **不主动 mask 密码字段**——但这些字段在落库前会被 `RedactingMessageConverter` 拿到吗？**不会**，因为审计是直接写 DB，不是日志。**密码 / token 类字段需要在 DTO 上加 `@JsonIgnore` 或显式不进入 controller 入参**

实践建议：

- `LoginRequest.password` / `DatasourceRequest.credential` 等敏感字段在审计 JSON 里**会被原文记录**——必须保证这些 controller 不带 `@AuditLog`，或在落库前 service 层先脱敏后再记
- 当前 `/api/auth/login` 带 `@AuditLog` 但不会写明文密码，因为 `LoginRequest` 字段名是 `password` 但**实际落库 JSON 里包含明文**——这是一个**已知风险**，按 OWASP 应在 `AspectArgUtils` 增加字段名黑名单（password / secret / token / apiKey）

## 查询

`/api/admin/audit-logs`，需要 `@RequireRole(SUPER_ADMIN)`：

```
GET /api/admin/audit-logs?
    userId=...&
    module=DATASOURCE&
    action=DELETE&
    status=FAILURE&
    startTime=2026-01-01T00:00:00&
    endTime=2026-04-30T23:59:59&
    page=1&pageSize=50
```

返回分页后的 `AuditLog` 实体列表。前端管理页支持组合过滤、CSV 导出。

## 数据量与归档

| 入口 | 频率 | 单条大小 |
|:---|:---|:---|
| `LOGIN` | 中等（用户登录每日 N 次） | 小 |
| `CRUD` | 低（人工触发） | 中（带 request_params） |
| `EXECUTE / RUN` | **高**（每次任务触发） | 中 |
| AI `CHAT / AGENT_RUN` | **高**（每次会话） | 中 |
| `DISPATCH` | **高**（每个任务节点 RPC） | 中 |

千用户、千日活的部署，估算每天 10 万 ~ 100 万行；半年 ~ 一年表会膨胀到上千万行。

### 归档建议

1. 按月分区：`PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at))`
2. 老分区按年迁到归档库，保留主库最近 6 ~ 12 个月
3. 或定期 `INSERT INTO archive_audit_log SELECT * FROM t_r_audit_log WHERE created_at < ?` + 删除原表
4. 长期合规（≥ 5 年）建议导出 Parquet 落 S3 / OSS

Rudder 当前**不内置归档**，需要 DBA 自行实现。

## 审计与脱敏的关系

| 出口 | 谁负责 |
|:---|:---|
| `t_r_audit_log` 写入 | `@AuditLog` aspect — 不经过 RedactionService |
| 日志（logback）`%msg` | `RedactingMessageConverter` — 经 RedactionService.scrubText |
| 查询结果 / AI 输出 | `RedactionService.applyMapRows / scrubText` |

**审计表里的 `request_params` 不被 RedactionService 处理**，因为它是结构化业务数据，规则的 TEXT 模式不适合扫 JSON。如需在审计上脱敏，正确做法是：

1. 在 controller 入参 DTO 上对敏感字段标 `@JsonIgnore`（不进 audit）
2. 或在 `AspectArgUtils` 加字段名黑名单
3. 不要靠 `RedactionService` 兜底审计

## 扩展

### 新审计动作

```
1. AuditModule / AuditAction / AuditResourceType 加枚举值
2. 在 controller 方法上标 @AuditLog(module=..., action=..., resourceCode="...")
3. 重启即生效（aspect 自动拾取）
```

### 不审计的写操作

只在需要追溯的端点上加 `@AuditLog`，不全打。当前 88 个端点带审计。

### 服务级审计（无 HTTP 上下文）

`@AuditLog` 也可以打在 service 方法上（aspect 兼容）。`HttpServletRequest` 缺失时 `request_ip / request_method / request_uri` 落 `unknown` / null；`UserContext` 缺失时 `username = "system"`。

## 常见审计场景

### 谁删了我的工作流

```sql
SELECT username, request_ip, created_at, status, error_message
FROM t_r_audit_log
WHERE module = 'WORKFLOW'
  AND action = 'DELETE'
  AND resource_code = <workflow_code>
ORDER BY created_at DESC;
```

### 谁登录失败了

```sql
SELECT username, request_ip, created_at, error_message
FROM t_r_audit_log
WHERE module = 'AUTH'
  AND action IN ('LOGIN', 'LOGIN_LDAP')
  AND status = 'FAILURE'
  AND created_at > NOW() - INTERVAL 1 DAY
ORDER BY created_at DESC;
```

### SPI 配置最近变更

```sql
SELECT username, module, action, request_params, created_at
FROM t_r_audit_log
WHERE action LIKE 'UPDATE_%CONFIG'
  AND created_at > NOW() - INTERVAL 7 DAY
ORDER BY created_at DESC;
```

### 哪些超管动作

```sql
SELECT al.* FROM t_r_audit_log al
JOIN t_r_user u ON u.id = al.user_id
WHERE u.is_super_admin = 1
  AND al.created_at > NOW() - INTERVAL 30 DAY;
```

## 排障

| 症状 | 排查 |
|:---|:---|
| 操作了但 `t_r_audit_log` 没记录 | 该端点没打 `@AuditLog`；查 controller 注解 |
| `username = "system"` | UserContext 缺失（如 RPC 调用 / 异步任务）；正常现象，不是 bug |
| `resource_code = null` | SpEL 表达式解析失败；看启动 / 运行日志 WARN `Failed to resolve @AuditLog resourceCode` |
| 审计写入慢 / 失败 | 队列满（`queue-capacity`）；调大或排查 DB 慢查询 |
| 审计表过大查询慢 | 加 `(module, action, created_at)` 索引；按月分区；归档老数据 |
| `request_params` 包含密码 | 敏感字段进入了 controller 入参；改 `@JsonIgnore` 或字段名黑名单 |

## 监控

| Metric | 含义 |
|:---|:---|
| `rudder_audit_record_total{module,action,status}` | 审计记录计数 |
| `rudder_audit_record_duration_seconds{module,action}` | 业务方法耗时 |
| `rudder_audit_async_queue_size` | 异步队列堆积深度 |
| `rudder_audit_async_rejected_total` | 队列满被拒次数（要告警） |

异常告警：`rejected_total > 0`、`queue_size > 800`（接近 1000 上限）。

## 相关文档

- [JWT](jwt.md) — `UserContext` 来源
- [SSO](sso.md) — 登录审计字段
- [数据脱敏](../redaction.md) — 业务出口的脱敏（与审计互补）
- [权限模型](../permissions.md) — 谁能看审计表
- [数据库 Schema](../database-schema.md#平台基础5) — `t_r_audit_log` 表结构
