# 任务类型

> Rudder 支持 **13 种数据任务节点 + 4 种控制流节点**。本章列出每种 `TaskType` 的 params 字段、数据源要求、流批支持。控制流节点完整语义见 [工作流](workflow.md#控制流节点)。

## 总览

| TaskType | 类别 | 数据源 | 执行模式 | 资源文件 | Params 类 |
|:---|:---|:---|:---|:---|:---|
| `HIVE_SQL` | SQL | HIVE | BATCH | — | `SqlTaskParams` |
| `STARROCKS_SQL` | SQL | STARROCKS | BATCH | — | `SqlTaskParams` |
| `MYSQL` | SQL | MYSQL | BATCH | — | `SqlTaskParams` |
| `DORIS_SQL` | SQL | DORIS | BATCH | — | `SqlTaskParams` |
| `POSTGRES_SQL` | SQL | POSTGRES | BATCH | — | `SqlTaskParams` |
| `CLICKHOUSE_SQL` | SQL | CLICKHOUSE | BATCH | — | `SqlTaskParams` |
| `TRINO_SQL` | SQL | TRINO | BATCH | — | `SqlTaskParams` |
| `SPARK_SQL` | SQL | SPARK | BATCH | — | `SqlTaskParams` |
| `FLINK_SQL` | SQL | FLINK | BATCH / **STREAMING** | — | `SqlTaskParams` |
| `SPARK_JAR` | JAR | — | BATCH | jar | `SparkJarTaskParams` |
| `FLINK_JAR` | JAR | — | BATCH / **STREAMING** | jar | `FlinkJarTaskParams` |
| `PYTHON` | 脚本 | — | BATCH | — | `ScriptTaskParams` |
| `SHELL` | 脚本 | — | BATCH | — | `ScriptTaskParams` |
| `HTTP` | API | — | BATCH | — | `HttpTaskParams` |
| `SEATUNNEL` | 数据集成 | — | BATCH | — | `SeaTunnelTaskParams` |
| `CONDITION` | 控制流 | — | Server 编排 | — | `ConditionTaskParams` |
| `SWITCH` | 控制流 | — | Server 编排 | — | `SwitchTaskParams` |
| `SUB_WORKFLOW` | 控制流 | — | Server 编排 | — | `SubWorkflowTaskParams` |
| `DEPENDENT` | 控制流 | — | Server 编排 | — | `DependentTaskParams` |

> 流批支持：`FLINK_SQL` 与 `FLINK_JAR` 同时支持 BATCH / STREAMING；STREAMING 模式下任务作为长跑实例由 `ManagedJobTask` 接管生命周期（启停 / Savepoint），不进入 Cron 调度。

## 公共字段（所有 TaskParams）

```java
abstract class AbstractTaskParams {
    List<TaskParam> inputParams;     // IN，运行前由 varPool 解析 ${var}
    List<TaskParam> outputParams;    // OUT，结束后由 TaskOutputParameterParser 写回 varPool
    Map<String,String> getResourceFiles();  // 需要 FileStorage 下载的文件，默认空
}

class TaskParam {
    String name;
    String value;
    ParamDirection direction;        // IN / OUT
    ParamType type;                  // STRING / INTEGER / LONG / BOOLEAN / LIST
}
```

参数注入与回写见 [工作流 · 参数传递](workflow.md#参数传递)。

## SQL 任务

所有 SQL 类（9 种）共用 `SqlTaskParams`：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `dataSourceId` | Long | 必填，绑定 `t_r_datasource.id` |
| `sql` | String | 必填，可含 `${var}` / `$[time]` |
| `sqlType` | `SqlType` | `QUERY` / `NON_QUERY`，未填时 `detectSqlType()` 按首关键字判定 |
| `executionMode` | String | `BATCH`（默认）；`FLINK_SQL` 可填 `STREAMING` |
| `queryLimit` | int | 查询返回行数上限，默认 `1000` |
| `preStatements` | List&lt;String&gt; | 主 SQL 前依次执行（如 `SET ...`） |
| `postStatements` | List&lt;String&gt; | 主 SQL 后依次执行（如清理） |
| `engineParams` | Map&lt;String,String&gt; | 引擎特定参数（透传到 JDBC URL 或 SET 语句） |

`SqlType` 自动检测规则：以 `SELECT / SHOW / DESCRIBE / DESC / EXPLAIN / WITH` 开头视为 `QUERY`，其余 `NON_QUERY`。

### 引擎差异

| 引擎 | 三层 / 两层 | 备注 |
|:---|:---|:---|
| HIVE | `database.table` | Hive2 JDBC，需 Kerberos 时通过 `params` 注入 |
| STARROCKS / DORIS / MYSQL | `database.table` | 走 MySQL JDBC driver |
| POSTGRES | `schema.table` | |
| CLICKHOUSE | `database.table` | 用 ClickHouse 官方 JDBC |
| TRINO | `catalog.schema.table` | 唯一三层 |
| SPARK | `database.table` | 走 Hive2 JDBC（连 Thrift Server） |
| FLINK | `catalog.database.table` | Flink JDBC + 流批模式 |

> `DatasourceType` 中 `hasCatalog=true` 的引擎（STARROCKS / TRINO）暴露 catalog 维度。

## JAR 任务

### `SparkJarTaskParams`

```
继承 AbstractJarTaskParams（mainClass / jarPath / args / deployMode / appName / engineParams）
+ master            yarn / k8s 等
+ queue             YARN 队列
+ resource          SparkResourceConfig（driver/executor 资源）
```

### `FlinkJarTaskParams`

```
继承 AbstractJarTaskParams
+ executionMode             BATCH / STREAMING
+ savepointPath             从 savepoint 恢复
+ allowNonRestoredState     允许丢弃 savepoint 中无对应算子的状态
+ resource                  FlinkResourceConfig（slot / parallelism）
```

`AbstractJarTaskParams.getResourceFiles()` 自动把 `jarPath` 注册成需要 FileStorage 下载的资源；Execution 启动前下载到 `resolvedFilePaths.get("jarPath")`。

## 脚本任务

### `ScriptTaskParams`（PYTHON / SHELL 共用）

```
content     脚本正文（必填）
```

执行规则：

- Execution 端落盘到 `RUDDER_EXECUTION_WORK_DIR/<taskInstance>/main.py | main.sh`
- Python 走 `python3`，Shell 走 `/bin/bash`
- stdin 关闭、stdout/stderr 实时落 `t_r_task_instance.log_path`
- 进程退出码 0 即成功，其它失败
- `outputParams` 通过约定行 `##rudder-output-param-{name}={value}` 解析

## HTTP 任务

`HttpTaskParams`：

| 字段 | 默认 | 说明 |
|:---|:---|:---|
| `url` | — | 必填，支持 `${var}` / `$[time]` |
| `method` | `GET` | `GET / POST / PUT / PATCH / DELETE` |
| `headers` | — | 请求头 |
| `body` | — | 请求体 |
| `contentType` | `application/json` | 仅 body 非空时设置 |
| `successCodes` | `[200]` | 不在列表中的状态码视为失败 |
| `connectTimeoutMs` | `10000` | 连接超时 |
| `readTimeoutMs` | `60000` | 读超时 |
| `retries` | `0` | 失败重试次数 |
| `retryDelayMs` | `1000` | 重试间隔 |
| `expectedBodyContains` | — | 响应 body 校验（包含子串） |

适合：调外部 API、触发下游平台 webhook、发钉钉 / 飞书消息。

## SeaTunnel

`SeaTunnelTaskParams`：

| 字段 | 默认 | 说明 |
|:---|:---|:---|
| `content` | — | HOCON / JSON 配置正文，必填 |
| `deployMode` | `cluster` | `local` / `cluster` |

由 Execution 落盘 `<workdir>/seatunnel.conf` 后调用 `seatunnel.sh --config <file>`。

## 控制流任务（详见 [工作流](workflow.md#控制流节点)）

### `ConditionTaskParams`

```
dependence
  relation                AND / OR
  dependTaskList[]
    relation              AND / OR
    dependItemList[]
      depTaskCode         上游节点 code
      status              SUCCESS / FAILED / ...
conditionResult
  successNode[]           满足时进入
  failedNode[]            不满足时进入
```

### `SwitchTaskParams`

```
switchResult
  dependTaskList[]
    condition             表达式（可含 ${var}）
    nextNode              命中时进入
  nextNode                默认分支
```

### `SubWorkflowTaskParams`

```
workflowDefinitionCode    被调起的子工作流
```

### `DependentTaskParams`

```
dependence
  relation                AND / OR
  checkInterval           秒级轮询
  failurePolicy           DEPENDENT_FAILURE_FAILURE / WAITING
  failureWaitingTime      失败等待小时
  dependTaskList[]
    relation              AND / OR
    dependItemList[]
      dependentType       DEPENDENT
      projectCode / definitionCode / depTaskCode
      cycle               DAY / HOUR / WEEK / MONTH
      dateValue           TODAY / LAST / THIS / ...
      parameterPassing    是否传递参数
```

字段含义对齐 DolphinScheduler，便于双向迁移。

## SPI 模块映射

```
rudder-spi/rudder-task/
├── rudder-task-api          抽象 + 公共 SqlTaskParams / ScriptTaskParams / AbstractJarTaskParams / TaskType
├── rudder-task-mysql / postgres / hive / starrocks / doris / clickhouse / trino / spark / flink
├── rudder-task-python / shell
├── rudder-task-http
└── rudder-task-seatunnel
```

每个 provider 模块包含：`<Name>TaskChannel` + `<Name>TaskChannelFactory` + 引擎特定的 `<Name>TaskParams`（如有）。控制流的 4 个 TaskType 不在 SPI，而是在 `rudder-service-server/.../workflow/controlflow/`。

## 添加新 TaskType

1. 新建 `rudder-spi/rudder-task/rudder-task-<name>` 模块
2. 在 `TaskType.java` 加枚举值（`label / ext / category / needsDatasource / datasourceType / executionModes`）
3. 实现 `TaskChannel` + `TaskChannelFactory`，工厂打 `@Component`
4. 必要时新建 `<Name>TaskParams extends AbstractTaskParams`（**必须放在新模块，不要塞 task-api**）
5. 把模块加入 `rudder-bundles/rudder-bundle-execution`（任务类）或 `rudder-bundle-api`（如需在 Server 暴露）
6. 前端在 `rudder-ui` 注册节点类型与配置面板

## 相关文档

- [工作流](workflow.md) — 节点如何被编排
- [数据源](datasource.md) — `dataSourceId` 解析与凭证管理
- [配置参考](configuration.md) — Execution 工作目录 / 文件存储相关
