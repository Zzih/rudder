# 工作流

> Rudder 的工作流由 DAG（有向无环图）+ 任务节点 + 控制流节点组成，支持手工触发与 Cron 调度。本章涵盖数据模型、调度语义、控制流、参数传递、生命周期。

## 数据模型

```
t_r_workflow_definition         工作流定义（dag_json + 全局参数）
   └─ t_r_task_definition       任务定义（每个 DAG 节点对应一行）
   └─ t_r_workflow_schedule     Cron 调度（一对一或多对一可选）
   └─ t_r_workflow_instance     运行实例（每次触发新增）
        └─ t_r_task_instance    任务实例（每个节点的本次运行）
```

核心字段：

| `t_r_workflow_definition` | 含义 |
|:---|:---|
| `code` | 工作流稳定标识（雪花 ID，跨版本不变） |
| `workspace_id` / `project_code` | 归属空间 / 项目 |
| `name` / `description` | 名称与描述 |
| `dag_json` | 节点 + 边 + 节点位置（LONGTEXT） |
| `global_params` | 工作流级全局参数（JSON） |
| `published_version_id` | 当前已发布版本 |

| `t_r_workflow_schedule` | 含义 |
|:---|:---|
| `workflow_definition_code` | 关联的工作流 |
| `cron_expression` | 标准 cron（6 字段） |
| `start_time` / `end_time` | 生效区间 |
| `timezone` | 例 `Asia/Shanghai` |
| `status` | `ONLINE` / `OFFLINE` |

## DAG 模型

`dag_json` 反序列化为：

```json
{
  "nodes": [
    { "taskCode": "1842847349239218176", "label": "load_dim_user", "position": { "x": 100, "y": 200 } },
    { "taskCode": "1842847349239218177", "label": "if_today_is_monday" }
  ],
  "edges": [
    { "source": "1842847349239218176", "target": "1842847349239218177" }
  ]
}
```

- `taskCode` 即 `t_r_task_definition.code`（雪花 ID，前端用字符串避免精度丢失）
- `position` 仅前端可视化用
- `edges` 单向，禁止环（提交时由 `DagParser` 校验）

## 节点类型

### 任务节点

落表 `t_r_task_definition`，13 种 `TaskType` 之一，详见 [任务类型](task-types.md)：

```
SQL：HIVE_SQL / STARROCKS_SQL / MYSQL / DORIS_SQL / POSTGRES_SQL / CLICKHOUSE_SQL / TRINO_SQL / SPARK_SQL / FLINK_SQL
JAR：SPARK_JAR / FLINK_JAR
脚本：PYTHON / SHELL
API：HTTP
集成：SEATUNNEL
```

任务节点由 Server 通过 RPC 派发到 Execution 执行。

### 控制流节点

控制流节点**不派发**到 Execution，由 Server 端 `WorkflowInstanceRunner` 在工作流编排过程里直接编排。

| TaskType | 作用 |
|:---|:---|
| `CONDITION` | 条件分支：评估上游节点状态，决定走 success / failed 分支 |
| `SWITCH` | 多路分支：按表达式命中第一个匹配 case，否则走默认 |
| `SUB_WORKFLOW` | 调起另一个工作流定义，作为整体节点等待其完成 |
| `DEPENDENT` | 依赖节点：等待其它工作流 / 任务在指定周期内成功 |

#### CONDITION

`ConditionTaskParams` 形态：

```json
{
  "dependence": {
    "relation": "AND",
    "dependTaskList": [
      {
        "relation": "AND",
        "dependItemList": [
          { "depTaskCode": 1842..., "status": "SUCCESS" }
        ]
      }
    ]
  },
  "conditionResult": {
    "successNode": [1842847349239218180],
    "failedNode": [1842847349239218181]
  }
}
```

`dependence` 命中 → 走 `successNode`，否则走 `failedNode`。

#### SWITCH

```json
{
  "switchResult": {
    "dependTaskList": [
      { "condition": "${env} == 'prod'", "nextNode": 1842847349239218200 },
      { "condition": "${env} == 'staging'", "nextNode": 1842847349239218201 }
    ],
    "nextNode": 1842847349239218299
  }
}
```

按顺序匹配第一个 `condition` 为 true 的分支；都不命中走 `switchResult.nextNode`（默认）。表达式中的 `${var}` 由 `VarPoolManager` 解析。

#### SUB_WORKFLOW

```json
{ "workflowDefinitionCode": 1842847349239218400 }
```

子工作流继承父 `varPool`，同步执行；子流任意节点失败决定父节点状态。

#### DEPENDENT

```json
{
  "dependence": {
    "relation": "AND",
    "checkInterval": 10,
    "failurePolicy": "DEPENDENT_FAILURE_FAILURE",
    "failureWaitingTime": 1,
    "dependTaskList": [
      {
        "relation": "AND",
        "dependItemList": [
          {
            "dependentType": "DEPENDENT",
            "projectCode": 100,
            "definitionCode": 200,
            "depTaskCode": 300,
            "cycle": "DAY",
            "dateValue": "TODAY",
            "parameterPassing": false
          }
        ]
      }
    ]
  }
}
```

- `checkInterval` 秒级轮询
- `failurePolicy`：依赖失败时立即失败 / 等待
- `cycle` × `dateValue`：与 DolphinScheduler 语义对齐（DAY/HOUR/WEEK × TODAY/LAST/THIS）

## 调度

### 触发方式

| 方式 | 来源 |
|:---|:---|
| **手工触发** | UI / `POST /api/workflow-instances` |
| **Cron 调度** | `t_r_workflow_schedule.cron_expression` 由 Server 内置 Quartz 解析 |
| **API 触发** | 第三方系统调 REST 触发实例 |
| **DolphinScheduler 触发** | 已发布到 DS 的工作流由 DS 调度，详见 [DolphinScheduler 集成](dolphinscheduler.md) |

### Cron 字段

6 字段（秒 分 时 日 月 周），例：

```
0 0 2 * * ?       每天 02:00
0 */15 * * * ?    每 15 分钟
0 0 9 ? * MON-FRI 工作日 09:00
```

`timezone` 不填默认 `Asia/Shanghai`。

### 实例生命周期

```
RUNNING → SUCCESS
        → FAILED
        → KILLED        （用户手工终止）
        → INTERRUPTED   （Server / Execution 重启时被打断，等待补救）
```

每个 `t_r_workflow_instance` 包含完整 `varPool` 快照、入参、退出码。任务实例 `t_r_task_instance` 同理，并保留 RPC 派发记录、日志路径。

### 失败策略

- **节点失败**：默认整工作流标 `FAILED` 并停止后续节点
- **重试**：任务级 `retry_times` × `retry_interval`（在 `t_r_task_definition`）
- **超时**：任务级 `timeout`，触发后由 Execution kill 进程
- **跳过**：`CONDITION` 走 failed 分支视为正常路径，不算失败

## 参数传递

### 三级覆盖

```
项目参数（Project.params）  <  工作流全局参数（WorkflowDefinition.global_params）  <  运行时参数
```

低优先级被高优先级**整字段覆盖**。

### 任务级输入 / 输出

```java
public class TaskParam {
    private String name;
    private String value;
    private ParamDirection direction;  // IN / OUT
    private ParamType type;            // STRING / INTEGER / LONG / BOOLEAN / LIST
}
```

- `inputParams`（IN）：任务运行前由 `VarPoolManager` 解析 `${var}` 占位符
- `outputParams`（OUT）：任务结束后由 `TaskOutputParameterParser` 从 stdout / 结果中提取，写回 `varPool`

写回的变量对**下游节点**可见，构成 DAG 间的参数管线。

### 时间占位符

`$[yyyyMMdd]` 类占位符由 `TimePlaceholderUtils` 独立解析，**不参与节点间传递**，直接基于触发时间渲染：

```
$[yyyyMMdd]            20251225
$[yyyy-MM-dd]          2025-12-25
$[yyyy-MM-dd-1]        2025-12-24（减 1 天）
$[HH:mm:ss]            13:45:00
```

### 全局参数示例

```json
{
  "env": { "prop": "env", "direction": "IN", "type": "STRING", "value": "prod" },
  "ds":  { "prop": "ds",  "direction": "IN", "type": "STRING", "value": "$[yyyy-MM-dd-1]" }
}
```

任务里写 `WHERE dt = '${ds}'` 即可。

## 编排执行器

Server 端核心：

| 组件 | 职责 |
|:---|:---|
| `DagParser` | dag_json → DagGraph + 拓扑排序 + 环检测 |
| `WorkflowInstanceRunner` | 实例驱动器：取就绪节点、派发、等待完成事件 |
| `CompletionEventRouter` | 收 RPC 回调 / 控制流完成事件，更新状态 |
| `VarPoolManager` | 三级参数合并 + 占位符替换 |
| `ControlFlowTaskFactory` | 控制流节点的本地执行 |
| `WorkflowOrphanReaper` | Server 重启后扫描 INTERRUPTED 实例做收尾 |
| `ResumeStateReconciler` | 节点状态对齐（DB 与 RPC 状态不一致时仲裁） |

并发上限：`rudder.workflow.executor-threads`（默认 100），见 [配置参考](configuration.md#七工作流执行器)。

## 发布流程

```
草稿 → 提交审批（可选） → 审批通过 → 发布到 published_version_id
                                          ↓
                                    （可选）发布到 DolphinScheduler
```

- 审批渠道走 `t_r_approval_*`，由 Approval SPI 完成（飞书 / Slack / KissFlow）
- 发布到 DS 见 [DolphinScheduler 集成](dolphinscheduler.md)

## 常见模式

### 跑批 + 通知

```
[load_data] → [aggregate] → [export_csv] → [HTTP 调度报警]
```

### 条件回流

```
[fetch_remote] → [CONDITION row_count > 0] → success: [load_to_warehouse]
                                          → failed:  [HTTP alert]
```

### 子工作流编排

```
[etl_dim_user] → [SUB_WORKFLOW: dwd_load] → [SUB_WORKFLOW: dws_load] → [SUB_WORKFLOW: ads_load]
```

### 跨工作流依赖

```
[本工作流首节点 = DEPENDENT 等待 dim_loader 当日 SUCCESS] → [aggregate] → ...
```

## 排障

| 症状 | 检查 |
|:---|:---|
| 节点一直 PENDING | Execution 是否 ONLINE（`t_r_service_registry`）；RPC `auth-secret` 是否一致 |
| `${var}` 未被替换 | 该变量是否在 `globalParams` 或上游 `outputParams` 里产出；变量名大小写 |
| Cron 不触发 | `t_r_workflow_schedule.status=ONLINE`；`start_time/end_time` 是否覆盖当前；服务时区与 cron timezone |
| 实例卡 INTERRUPTED | Server 重启后 `WorkflowOrphanReaper` 介入；如果一直没动，看 Server 日志是否抛错 |
| 控制流分支走错 | 表达式里的 `${var}` 是否解析为字符串（带引号），数值比较记得显式转 |

## 相关文档

- [任务类型](task-types.md) — 各 TaskType 的 params 字段
- [DolphinScheduler 集成](dolphinscheduler.md) — 发布到 DS 后的运行模式
- [权限模型](permissions.md) — 谁能创建 / 发布 / 触发工作流
