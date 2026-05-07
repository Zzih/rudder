# DolphinScheduler 集成

> Rudder 提供两种调度形态：内置 Cron（适合开发 / 轻量场景）与对接 DolphinScheduler（生产级调度）。本章讲对接 DS 的发布流程、字段映射、运维。

## 为什么需要对接 DS

Rudder 内置 Cron 调度依赖 Server 进程稳定，不具备：

- 任务级 SLA / 告警 / 失败补数
- 跨集群任务调度网关、统一计算资源池
- 调度审计、变更审批工作流
- 与企业现有 DS 集群整合

因此 Rudder 选择**控制平面留在 Rudder（IDE / 工作流编排 / 元数据 / 数据源）**，**调度平面外移到 DolphinScheduler**。Rudder 的工作流模型在设计时就对齐了 DS：DAG 节点、控制流（CONDITION / SWITCH / SUB_WORKFLOW / DEPENDENT）、参数传递、Cron 字段一一映射。

## 架构

```
┌──────────────────────────────────────┐
│  Rudder (IDE / 编排 / 数据源 / 元数据) │
│                                       │
│  发布按钮  ──────►  ArionDolphinPublishService
│                                ↓
│                        Arion 网关 (HTTP) ←── ARION_DOLPHIN_CLIENT_URL
│                                ↓
│                    DolphinScheduler API
│                                ↓
│                    DS Master / Worker 调度任务
└──────────────────────────────────────┘
```

`Arion` 是 Rudder 团队维护的 DS 发布网关（Spring Boot 应用，独立部署），把 Rudder 的工作流定义翻译成 DS 的 Project / Workflow / Schedule。

## 配置

`.env` 或 `application.yml`：

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `ARION_DOLPHIN_CLIENT_URL` | `http://127.0.0.1:12348` | Arion 网关 URL |
| `ARION_DOLPHIN_CLIENT_TOKEN` | 空 | Arion 网关鉴权 token |

`ArionDolphinPublishService` 由 `@ConditionalOnProperty(prefix = "arion-dolphin.client", name = "url")` 守护——**不配 URL 就不加载 bean，发布按钮变灰**。

## 发布流程

```
草稿 → 审批（可选） → 上线（published_version_id）
                            │
                            └─► 发布到 DolphinScheduler（按需）
                                ├─ POST /publish-workflow   单工作流
                                └─ POST /publish-project    项目级批量
```

发布 = 把 Rudder 当前 published 版本对应的 DAG / 任务定义 / Cron 全量推到 DS，覆盖 DS 同名工作流。

### 单工作流发布

```java
arionDolphinPublishService.publish(workflow, userName);
```

构造 `WorkflowPublishRequest`：

| Rudder 字段 | DS 字段 |
|:---|:---|
| `WorkflowDefinition.name` | DS workflow name |
| `WorkflowDefinition.dag_json` | DS taskDefinitions + taskRelations |
| `WorkflowDefinition.global_params` | DS globalParams |
| `WorkflowSchedule.cron_expression` | DS schedule.crontab |
| `WorkflowSchedule.timezone` | DS schedule.timezoneId |
| `WorkflowSchedule.start_time/end_time` | DS schedule.startTime/endTime（`yyyy-MM-dd HH:mm:ss`） |
| `Project.name` | DS project name |
| `userName` | DS userName（前置在 DS 已建好同名用户） |

### 项目级批量发布

```java
arionDolphinPublishService.publishProject(workflows, userName, ...);
```

一次请求包含 project 下多个 workflow，适合：

- 跨工作流依赖（`DEPENDENT` 节点）整体上线，避免分别发布造成短暂悬空
- 项目级回滚（一次性回到上一发布快照）

## 字段映射

### TaskType

| Rudder TaskType | DS TaskType |
|:---|:---|
| `HIVE_SQL / STARROCKS_SQL / MYSQL / DORIS_SQL / POSTGRES_SQL / CLICKHOUSE_SQL / TRINO_SQL / SPARK_SQL / FLINK_SQL` | `SQL`（按数据源类型分发） |
| `SPARK_JAR` | `SPARK` |
| `FLINK_JAR` | `FLINK_STREAM` / `FLINK` |
| `PYTHON` | `PYTHON` |
| `SHELL` | `SHELL` |
| `HTTP` | `HTTP` |
| `SEATUNNEL` | `SEATUNNEL` |
| `CONDITION` | `CONDITIONS` |
| `SWITCH` | `SWITCH` |
| `SUB_WORKFLOW` | `SUB_PROCESS` |
| `DEPENDENT` | `DEPENDENT` |

### 参数

`Property` 模型在两边的字段一一对应：

```
Rudder              DS
prop / name      → property name
direction(IN/OUT)→ direct
type             → type (VARCHAR / INTEGER / ...)
value            → value
```

时间占位符 `$[yyyy-MM-dd-1]` 在 DS 也是同名语法，跨边迁移无需重写。

### 调度

```
WorkflowSchedule.cronExpression  →  schedule.crontab
WorkflowSchedule.timezone        →  schedule.timezoneId（默认 Asia/Shanghai）
WorkflowSchedule.startTime       →  schedule.startTime
WorkflowSchedule.endTime         →  schedule.endTime
```

DS 端的「上线 / 下线」开关由 Rudder 通过 `WorkflowSchedule.status (ONLINE/OFFLINE)` 同步。

## 发布记录

`t_r_publish_record` 跟踪每一次发布：

| 字段 | 含义 |
|:---|:---|
| `id` | 主键 |
| `publish_type` | `WORKFLOW` / `PROJECT` |
| `target_code` | 工作流 code 或项目 code |
| `version_id` | 关联 `t_r_version_record`（发布时打的版本快照） |
| `status` | `PENDING_APPROVAL` / `PUBLISHED` / `PUBLISH_FAILED` |
| `error_message` | 失败原因 |
| `created_by / created_at` | 操作人与时间 |

> 审批结果不再写回 `PublishStatus`，列表查询时关联 `t_r_approval_record` 现拉，避免双写不一致。

## 审批联动

如果该 Workspace 配了审批渠道（`t_r_approval_config`，飞书 / Slack / KissFlow），发布前会先创建 `t_r_approval_record`，等审批通过才调 Arion；否则直接发布。审批触发的回调由 `ApprovalCallbackController` 接收，详见 SPI 设计。

## 双写一致性

Rudder 与 DS 之间没有事务边界。可能的不一致：

| 场景 | 表现 | 处置 |
|:---|:---|:---|
| Arion 调用成功后 Rudder 落库失败 | DS 已上线，Rudder `PUBLISH_FAILED` | 重试发布按钮（DS 端被覆盖即可） |
| Arion 部分成功（项目级批量） | 部分 workflow 在 DS，部分没 | Arion 自身做幂等 / 回滚 |
| DS 端被人手工改了 | 与 Rudder 草稿出现漂移 | 下次发布会全量覆盖 |

**约定**：以 Rudder 为唯一编辑入口，DS 端只做执行。

## 运维

### 健康检查

- Arion `/healthz` —— 网关存活
- DS Master / Worker —— DS 自身体系
- `arionClient` 调用失败时 Rudder 日志：`io.github.zzih.rudder.service.workflow.ArionDolphinPublishService` 打 ERROR

### 多套 DS

一个 Arion 网关只对接一套 DS。多 DS（如 prod / staging 分集群）需要独立部署多个 Arion，Rudder 通过环境差异化配置 `ARION_DOLPHIN_CLIENT_URL`。

### 回滚

```
旧版本快照 → 重新发布
```

`t_r_version_record` 保留每次发布对应的 DAG 快照；回滚 = 把旧版本作为当前 published 版本再发一次到 DS。

## 排障

| 症状 | 排查 |
|:---|:---|
| 发布按钮不可点 | `arion-dolphin.client.url` 未配，`ArionDolphinPublishService` 没装载 |
| `401 Unauthorized` from Arion | `ARION_DOLPHIN_CLIENT_TOKEN` 未配 / 错配 |
| DS 端 workflow 字段不全 | Arion 版本与 Rudder 不匹配；升级 `arion.version` |
| Cron 在 DS 上不触发 | `WorkflowSchedule.status=OFFLINE`；或 `start_time` 大于当前时间 |
| 任务类型 `SUB_PROCESS` 找不到目标 | 子工作流没在同一 DS 项目里发布；批量发布或单独先发布子流 |
| 时区错乱 | DS Worker 的 `TZ` 与 Rudder `WorkflowSchedule.timezone` 不一致；统一为 `Asia/Shanghai` |

## 仅用内置 Cron 的姿势

不配 `ARION_DOLPHIN_CLIENT_URL` 即可：

- 工作流发布只更新 `published_version_id`
- 调度由 Rudder Server 内部 Quartz 触发
- 适合内部数据仓库、轻量场景；**注意 Server 必须保持运行**

## 相关文档

- [工作流](workflow.md) — Rudder 工作流模型
- [任务类型](task-types.md) — TaskType 与 DS 的映射依据
- [配置参考 - DolphinScheduler 对接](configuration.md#十二dolphinscheduler-对接)
