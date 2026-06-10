# 数据权限

> 数据权限子系统在「平台用户 / 工作空间」这套应用层 RBAC 之外,提供面向底层数据引擎(Hive / Spark / Trino / StarRocks 等)的表级、列级访问控制。它统一管理权限的申请、审批、授予与回收,并在两条独立的执行路径上拦截越权访问:Worker 本地 SQL 鉴权与 Apache Ranger 同步。
>
> 本章覆盖核心概念、数据模型、授权链路、两种鉴权模式与 REST 接口。平台侧配置项的逐项释义见 [数据权限平台配置指南](#平台配置)。应用层角色与接口鉴权见 [权限模型](permissions.md)。

## 概览

数据权限与 [权限模型](permissions.md) 描述的角色体系是两个正交的维度:

- **应用层权限** 约束用户能否进入某工作空间、能否编辑脚本 / 发布工作流,粒度到接口。
- **数据权限** 约束用户提交的 SQL 能访问哪些 catalog / database / table / column,粒度到列,在任务执行时 enforce。

数据权限提供两种**可独立或同时启用**的鉴权模式:

| 模式 | enforce 位置 | 适用场景 |
|---|---|---|
| **本地鉴权** | Worker 执行 SQL 前解析待访问资源,比对权限快照,缺权拒绝任务 | 无 Ranger 基础设施,或希望增加一层 Rudder 侧拦截 |
| **Ranger 同步** | Reconciler 将授权推送到 Apache Ranger,由各引擎的 Ranger Plugin 在查询时拦截 | 已有 Ranger 基础设施的部署 |

总开关打开后至少需启用其中之一。两者互相解耦,任一不可用不会阻塞另一层。

## 核心概念

| 概念 | 实体 / 表 | 说明 |
|---|---|---|
| **数据权限域(Scope)** | `t_r_data_perm_scope` | 受管控的逻辑域,绑定一个 `PluginType`(引擎类型)、一个元数据数据源,以及(本地鉴权下)一组受管任务类型。权限包与申请单都以 Scope 为单位组织资源。 |
| **操作分组(AccessGroup)** | `t_r_data_perm_scope_access_group` | 把 Plugin 原生操作(`select` / `insert` / `update` 等)按业务语义组合成命名分组,授权时引用分组而非散列操作。 |
| **权限包(Bundle)** | `t_r_data_perm_bundle` | RBAC 角色,名称全局唯一,由若干「作用域块」组成,可设置对工作空间的可见性。 |
| **作用域块(Statement)** | `t_r_data_perm_bundle_statement` | 权限包或直接授权内的最小编辑单位:一个 Scope + 一组操作分组 + 若干资源路径行。 |
| **资源路径(ResourcePath)** | `*_statement_resource` | catalog / database / table / column 四层定位,各层可为具体值、通配符或不适用。 |
| **授权(Grant)** | `t_r_data_perm_user_bundle_grant` / `t_r_data_perm_user_direct_grant` | 用户被授予的权限记录,分「权限包授权」与「直接授权」两类,均带生效 / 到期时间与失效溯源。 |
| **权限事实快照(Effective Snapshot)** | `t_r_data_perm_user_effective_snapshot` | 每轮 Reconciler 算出的全量用户事实权限。本地鉴权直接消费快照,Ranger 同步以快照为对账源。 |

### PluginType 与资源层级

`PluginType`(`service.dataperm.config.PluginType`)是 Scope 的引擎类型,决定可用操作与资源层级:

| PluginType | 对应引擎 | 原生操作 | 资源层级 |
|---|---|---|---|
| `HADOOP_SQL` | Hive / Spark Thrift / Impala(共用 Hive Ranger plugin) | select / update / create / drop / alter / all | database / table / column |
| `STARROCKS` | StarRocks 3.1.9+ | select / insert / update / delete / create_table / drop / alter / export / refresh | catalog / database / table / column |
| `TRINO` | Trino | select / insert / delete / create / drop / alter / use / all | catalog / schema / table / column |
| `HBASE` / `HDFS` / `KAFKA` | 预留扩展 | —— | —— |

资源层级枚举为 `ResourceLevel`:`CATALOG` / `DATABASE` / `SCHEMA` / `TABLE` / `COLUMN`。

## 数据模型

```
t_r_data_perm_config                        平台配置(单行:总开关 / 两种模式开关 / Ranger 接入 / Reconciler 策略)
t_r_data_perm_scope                         权限域(pluginType / metadataDatasourceId / managedTaskTypes / rangerServiceName)
t_r_data_perm_scope_access_group            操作分组(scopeCode / accesses)

t_r_data_perm_bundle                        权限包元信息(name 全局唯一)
t_r_data_perm_bundle_statement              权限包作用域块(bundleId / scopeCode / groupIds)
t_r_data_perm_bundle_statement_resource     权限包资源路径行(catalog/database/table/column names)

t_r_data_perm_user_bundle_grant             用户的权限包授权(userId / bundleId / sourceApprovalId / effective/expiration / endReason)
t_r_data_perm_user_direct_grant             用户的直接授权块(userId / scopeCode / groupIds / sourceApprovalId / ...)
t_r_data_perm_user_direct_grant_resource    直接授权资源路径行

t_r_data_perm_user_effective_snapshot       权限事实快照(userId / version / scope / 资源定位 / accesses / 来源)
```

权限包对工作空间的可见性通过通用的工作空间资源授权管理(资源类型 `DATA_PERM_BUNDLE`);只有可见的权限包才会在该工作空间的申请页展示。

## 授权链路

授权一律经审批落库,不允许直接写授权表:

```
用户申请(选权限包 / 直接授权 + 到期日)
  └─ DataPermApplyService.submit()
       ├─ 校验:至少一项非空、权限包存在、数据源已开放、到期日合法
       └─ 将申请内容序列化为 DataPermApplyContext 存入审批单 ext_data
  └─ ApprovalService.submit(resourceType = DATA_PERM_APPLY)
       └─ 阶段链由 DataPermApplyStageFlow 解析(WORKSPACE_OWNER → SUPER_ADMIN)

审批终态
  ├─ APPROVED → DataPermApprovalIntegration.onFinalized()
  │    ├─ 反查 ext_data → DataPermApplyContext
  │    ├─ 幂等:按 source_approval_id 查重(防重试重复写)
  │    ├─ 事务内写 bundle grant + direct grant(全有或全无)
  │    └─ 触发 Reconciler 立即执行一次(ClusterScheduler.triggerNow)
  └─ REJECTED / WITHDRAWN / EXPIRED → 业务表无操作
```

`source_approval_id` 同时是授权表的幂等键,保证审批回调重投递不会重复授权。审批集成的整体机制见 [审批](approval.md)。

授权的回收由管理员主动发起(按权限包 grant / 直接授权 grant / 整张申请单三种粒度),写入 `end_reason`(`REVOKED`),权限包被删除时关联授权级联失效(`ROLE_DELETED`),到期则由 Reconciler 标记 `EXPIRED`。

## 本地鉴权

本地鉴权在 Worker 侧、任务真正执行前完成,流程见平台指南的[本地鉴权工作流](#平台配置)。关键实现要点:

- **SQL 解析基于 Alibaba Druid SQL Parser**(`common.sql.SqlAccessResolver`)。早期使用 Calcite,现已统一迁移到 Druid 以支持完整 DML / DDL 解析。解析 pre / main / post 三段 SQL,产出待访问的资源与操作意图列表(`TableAccess`)。
- **受管任务类型驱动**:任务需实现 `DataPermAwareTask`。Worker 取任务的 TaskType 与发起人 user_id,按 TaskType 反查启用 Scope 的 `managedTaskTypes`;未命中任何 Scope 即放行(该任务类型不受管控)。TaskType 跨 Scope 不能重复绑定。
- **列级支持**:快照与比对覆盖到 column 层。
- **全限定预检**:当授权端某层级存在具体值(非通配、非不适用)时,SQL 解析出的访问意图必须也提供该层,否则无法判断访问目标,直接以 `LOCAL_AUTH_UNQUALIFIED_TABLE` 拒绝,防止通配符意外放宽权限。
- 缺权抛 `LOCAL_AUTH_DENIED`,任务标记失败并附缺权清单。

## Ranger 同步

启用 Ranger 同步时,`DataPermReconciler` 周期性地把当前所有活跃授权展开为 desired policy,通过 Ranger Admin REST API 创建 / 更新 / 删除 policy。引擎类型到 Ranger 资源的映射由 `RangerResourceAdapter`(经 `RangerAdapterRegistry` 按 PluginType 选取)处理。Scope 的 `rangerServiceName` 必须与 Ranger Admin 已建的 `service.name` 一致。

Scope 关闭后 Reconciler 跳过其下所有 desired 项,但**已写入 Ranger 的 policy 不会自动清除**。

## 集群调度与 Reconcile

Reconciler 注册到 `ClusterScheduler`,保证任意时刻全集群最多一个节点执行,避免多副本重复写 Ranger / 快照。详见 [架构总览 - 集群调度](architecture.md#clusterscheduler-集群单-leader-调度)。

触发方式:

- **周期触发**:间隔由配置 `reconcileIntervalSeconds` 决定(运行时热更新,无需重启)。
- **即时触发**:审批通过 / 授权回收时调 `ClusterScheduler.triggerNow`,跨周期补一次。

每轮算出的快照与上一版本差量对比,有变化才写新 version;Local 鉴权读最新版本,Ranger 同步以此对账。

## REST 接口

用户侧与管理侧端点统一挂在 `DataPermController`(`/api/data-perm`);平台配置端点归入 `ConfigController`(`/api/config`,见 [配置 Controller 合并约定](configuration.md))。

### 用户侧(`@RequireLoggedIn`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/data-perm/applications` | 提交数据权限申请 |
| GET | `/api/data-perm/my-grants/summary` | 我的权限概览(权限包卡片 + 直接授权) |
| GET | `/api/data-perm/my-grants/bundles/{bundleId}/permissions` | 我的某权限包内权限项(分页) |
| GET | `/api/data-perm/my-grants/direct/permissions` | 我的直接授权权限项(分页) |
| GET | `/api/data-perm/my-grants/history` | 我的已失效授权历史(分页) |
| GET | `/api/data-perm/bundles` | 当前工作空间可见的权限包 |
| GET | `/api/data-perm/bundles/{id}/statements` | 权限包作用域块(分页 + 搜索) |

### 管理侧(权限包管理 `@RequireSuperAdmin`,授权查询 `@RequireLoggedIn`)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/data-perm/bundles[/{id}]` | 权限包 CRUD |
| GET / PUT | `/api/data-perm/bundles/{id}/workspaces` | 权限包可见工作空间 |
| POST / PUT / DELETE | `/api/data-perm/bundles/{id}/statements[/{statementId}]` | 作用域块增改删 |
| GET | `/api/data-perm/admin/effective-snapshot` | **总览视图**:全量权限事实快照(分页,支持 `asOf` 时间旅行) |
| GET | `/api/data-perm/admin/grants/aggregated` | **我的数据权限视图**:按用户聚合活跃授权(分页) |
| GET | `/api/data-perm/admin/grants/items` | 聚合视图展开后的权限项 |
| GET | `/api/data-perm/admin/grants/by-user/{userId}` | 按用户列出当前活跃授权 |
| GET | `/api/data-perm/admin/users/search` | 搜索用户(工作空间限定) |
| POST | `/api/data-perm/admin/grants/bundle/{id}/revoke` | 撤销权限包授权 |
| POST | `/api/data-perm/admin/grants/direct/{id}/revoke` | 撤销直接授权 |
| POST | `/api/data-perm/admin/grants/by-approval/{id}/revoke` | 按申请单批量撤销 |

### 两个管理视图

- **总览(effective-snapshot)** — 行级粒度,一行 = 一个用户在某 Scope 下一条资源(`catalog_name` / `database_name` / `table_name` / `column_name`)上的 `accesses`,`source_kinds` 字段(JSON)溯源该权限来自哪些权限包或直接授权。数据来自权限事实快照表(每行带 `snapshot_time`),支持按时间点(`asOf`)回看历史版本。
- **我的数据权限(aggregated)** — 用户级粒度,一行 = 一个用户,列出其全部活跃授权来源(权限包 / 直接授权)与各自资源行数,点开某来源再调 `grants/items` 拉具体权限项。

## 平台配置

平台侧配置(总开关、两种模式开关、Ranger Admin 接入、Reconciler 策略)由 `DataPermConfigService` 管理,经 `GlobalCacheService` 缓存,运行时改配置即时生效。主要字段:

| 字段 | 适用模式 | 说明 |
|---|---|---|
| `enabled` | —— | 总开关 |
| `localModeEnabled` / `rangerModeEnabled` | —— | 两种鉴权模式开关,至少启用其一 |
| `rangerAdminUrl` / `rangerAdminUsername` / `rangerAdminPassword` | Ranger | Ranger Admin REST 接入信息 |
| `rangerAdminTimeoutMs` / `rangerAdminPageSize` | Ranger | Ranger Admin 请求超时与分页大小 |
| `ensureRangerUser` | Ranger | 写 policy 前是否确保 Ranger 端用户存在 |
| `rangerWriteConcurrency` | Ranger | Ranger Admin 写入并发,建议 4–8 |
| `reconcileIntervalSeconds` | 通用 | Reconciler 周期,建议 ≥ 300,热更新 |
| `reconcileLockTtlSeconds` | 通用 | 集群调度锁 TTL,须大于周期 |
| `reconcileBatchSize` | 通用 | 单轮处理用户数 |
| `reconcileFailureAlertThreshold` | Ranger | 连续失败告警阈值 |

配置相关端点(`ConfigController`,`/api/config`):

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/config/data-perm/enabled` | `@RequireLoggedIn` | 查询总开关状态 |
| GET | `/api/config/data-perm/scopes` | `@RequireLoggedIn` | 列出 Scope 元数据(申请页用) |
| GET | `/api/config/data-perm/scopes/{scopeCode}/access-groups` | `@RequireLoggedIn` | 列出某 Scope 的操作分组 |
| GET | `/api/config/data-perm/plugin-types` | `@RequireLoggedIn` | 列出支持的 PluginType |
| GET | `/api/config/data-perm/guide` | `@RequireLoggedIn` | 获取平台配置指南 |
| GET / PUT | `/api/config/data-perm` | `@RequireSuperAdmin` | 获取 / 保存完整配置(含 Scope 与操作分组) |
| POST | `/api/config/data-perm/test` | `@RequireSuperAdmin` | 测试 Ranger Admin 连通性 |
| POST | `/api/config/data-perm/reconcile-trigger` | `@RequireSuperAdmin` | 手动触发一次 Reconcile |

逐项释义、启用模式组合与配置流程参见仓库内置的平台配置指南(管理后台「数据权限」页内嵌,源文件 `rudder-api/src/main/resources/spi-guide/dataperm-platform.zh.md`)。

## 错误码

数据权限错误码段为 9300–9399(`DataPermErrorCode`),典型:

| 码 | 常量 | 含义 |
|---|---|---|
| 9301–9303 | `ROLE_NOT_FOUND` / `ROLE_NAME_DUPLICATE` / `ROLE_IN_USE` | 权限包不存在 / 重名 / 仍被引用无法删除 |
| 9310–9313 | `APPLICATION_INVALID` / `DATASOURCE_NOT_OPEN` / `RESOURCE_MISSING` / `EXPIRE_DATE_INVALID` | 申请数据非法 |
| 9320 | `GRANT_NOT_FOUND` | 授权记录不存在 |
| 9330–9334 | `ACCESS_GROUP_*` | 操作分组相关 |
| 9340–9355 | `RANGER_*` | Ranger 接入 / policy / service 相关 |
| 9352–9353 | `AT_LEAST_ONE_MODE_REQUIRED` / `LOCAL_MODE_NO_MANAGED_TASK_TYPE` | 模式 / Scope 校验 |
| 9354 | `LOCAL_AUTH_DENIED` | 本地鉴权缺权拒绝 |
| 9356 | `MANAGED_TASK_TYPE_DUPLICATE` | TaskType 跨 Scope 重复绑定 |
| 9357 | `LOCAL_AUTH_UNQUALIFIED_TABLE` | 全限定预检失败 |

## 相关文档

- [权限模型](permissions.md) — 应用层角色与接口鉴权
- [审批](approval.md) — 申请审批链路与 SPI
- [数据源](datasource.md) — Scope 绑定的元数据数据源
- [架构总览](architecture.md#clusterscheduler-集群单-leader-调度) — 集群调度
- [数据库 schema](database-schema.md) — 表结构
