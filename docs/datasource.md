# 数据源

> 数据源是 Rudder 与外部存储 / 计算引擎对接的统一入口。本章涵盖支持的引擎、凭证加密、Workspace 授权、生命周期。

## 数据模型

```
t_r_datasource              全局数据源池（不属于任何 workspace）
t_r_datasource_permission   workspace 级授权（多对多）
```

`t_r_datasource` 字段：

| 字段 | 类型 | 说明 |
|:---|:---|:---|
| `id` | Long | 主键 |
| `name` | String | **不可变**，参与 DataHub URN / OpenMetadata FQN，改名会断元数据连接 |
| `datasource_type` | String | `DatasourceType` 枚举值 |
| `host` / `port` | String / Int | 地址 |
| `database_name` | String | JDBC URL 模板里 host:port 后那段，各引擎语义不同（见下） |
| `params` | JSON | 额外连接参数（如 `{ "useSSL": "false" }`） |
| `credential` | JSON（密文） | 用 `RUDDER_ENCRYPT_KEY` AES 加密的用户名 / 密码 |

> `database_name` 在不同引擎语义不同：MySQL/PG → database；Hive → default schema；Trino → catalog。**仅用来构造 JDBC URL，禁止当业务身份匹配元数据**——一个数据源连接层面可以跨库浏览。

## 支持的引擎

由 `DatasourceType` 枚举定义：

| Type | JDBC Driver | URL 模板 | 三层 catalog |
|:---|:---|:---|:---:|
| `HIVE` | `org.apache.hive.jdbc.HiveDriver` | `jdbc:hive2://{host}:{port}/{db}` | |
| `STARROCKS` | `com.mysql.cj.jdbc.Driver` | `jdbc:mysql://...?useUnicode=true&...&useInformationSchema=false` | ✓ |
| `MYSQL` | `com.mysql.cj.jdbc.Driver` | `jdbc:mysql://...?useUnicode=true&useInformationSchema=true` | |
| `DORIS` | `com.mysql.cj.jdbc.Driver` | `jdbc:mysql://...?useSSL=false&allowPublicKeyRetrieval=true` | |
| `POSTGRES` | `org.postgresql.Driver` | `jdbc:postgresql://{host}:{port}/{db}` | |
| `CLICKHOUSE` | `com.clickhouse.jdbc.ClickHouseDriver` | `jdbc:clickhouse://{host}:{port}/{db}` | |
| `TRINO` | `io.trino.jdbc.TrinoDriver` | `jdbc:trino://{host}:{port}/{catalog}` | ✓ |
| `SPARK` | `org.apache.hive.jdbc.HiveDriver` | `jdbc:hive2://{host}:{port}/{db}` | |
| `FLINK` | `org.apache.flink.table.jdbc.FlinkDriver` | `jdbc:flink://{host}:{port}/{db}` | |

`DatasourceType.buildJdbcUrl(host, port, database)` 自动渲染模板，并在 `database` 为空时把尾部的 `/` 去掉避免 `jdbc:mysql://host:9030/?params` 这种残留。

`hasCatalog=true`（STARROCKS / TRINO）暴露 catalog 维度，元数据浏览呈现 `catalog → database → table` 三层；其它引擎呈现 `database → table` 两层。

## 凭证加密

```
原文 credential JSON  ── AES-CBC ──>  base64 密文  ──>  t_r_datasource.credential
                          ↑
                  RUDDER_ENCRYPT_KEY (≥ 32 字节)
```

- 写入：`DatasourceService.save()` 在落库前加密
- 读取：`DatasourceService.loadDecrypted()` 解密为 in-memory pojo
- 输出：响应 DTO 永远脱敏（密码字段隐藏 / 替换为 `***`）
- 密钥变更：见 [security/rotation.md](security/rotation.md)

> 一次性把 `RUDDER_ENCRYPT_KEY` 改了**不会做双密钥过渡**，所有现存数据源解密会失败。轮换流程务必走文档。

## 连接池

数据源连接由 `rudder-datasource` 模块统一管理：

- 每个 datasource 一个 HikariCP pool（lazy 初始化，首次使用时建池）
- 任务执行 / 元数据浏览 / SQL IDE 共用连接池
- 池大小、超时由内部默认值控制（暂无 UI 暴露）
- 数据源更新（host / 端口 / 凭证 / 参数变更）会触发 pool refresh

## 生命周期

```
新建 → 测试连接 → 保存 → 授权给 Workspace → 在 IDE / 任务里使用
                                  ↓
                              元数据同步（可选，对接 DataHub / OpenMetadata / JDBC）
                                  ↓
                              AI 上下文（如启用）
```

### 测试连接

`POST /api/datasources/{id}/test`（或新建时使用临时入参测试）：

1. 使用入参构造 JDBC URL
2. 解密 credential
3. 取 / 还连接（`Connection.isValid(timeout)`）
4. 返回 OK / 错误信息

测试失败也允许保存（适用于未上线的环境），运行任务时仍会再次校验。

### 删除

软删（`deleted_at`）。引用此数据源的 `t_r_task_definition` 不会强校验级联——删除前自行确认无活引用，否则任务运行会报 `Datasource not found`。

## Workspace 授权

```
t_r_datasource_permission ( id, datasource_id, workspace_id )
```

- 一个数据源可授权给多个 workspace；同一 workspace 看不到没授权的数据源
- `SUPER_ADMIN` 跳过授权检查，能看到 / 操作所有数据源
- 普通用户在 IDE 中 / 创建任务时只能选所在 workspace 已授权的数据源

授权操作入口：`管理 → 数据源 → 授权`，需要 `SUPER_ADMIN`。

```
SQL 一览：
  SELECT d.* FROM t_r_datasource d
  JOIN t_r_datasource_permission dp ON dp.datasource_id = d.id
  WHERE dp.workspace_id = ? AND dp.deleted_at IS NULL AND d.deleted_at IS NULL
```

## 跨工作空间共享

实践建议：

- 公共 ODS / DIM 数据源 → 授权给所有 workspace
- 业务专属库 → 仅授权给对应 workspace
- 生产 / 测试库分别建独立数据源（同 host:port 不同 `database_name` 也算一个），便于审计

## 元数据接入

对接元数据中心后，AI 助手 / 表浏览器可以拿到丰富的字段注释、上下游血缘等。

| Provider | 配置 |
|:---|:---|
| `JDBC`（兜底） | 直连 `DatabaseMetaData` 拉表 / 列；无需额外配置 |
| `DATAHUB` | 配置 GraphQL endpoint + token，按 `urn:li:dataset:(urn:li:dataPlatform:<type>,<name>.<table>,<env>)` 匹配 |
| `OPENMETADATA` | 配置 host + token，按 FQN `<service>.<db>.<schema>.<table>` 匹配 |

`Datasource.name` 同时作为 DataHub 的 `dataset` 名片段、OpenMetadata 的 service name，**改名 = 断连**。

详见 AI 模块的 [元数据 provider](ai/providers.md) 与 [知识库](ai/knowledge-base.md)。

## 在任务中使用

任务节点引用数据源的 ID，而不是名字：

```json
{
  "taskType": "MYSQL",
  "params": {
    "dataSourceId": 12,
    "sql": "SELECT * FROM dim.user WHERE dt = '${ds}'"
  }
}
```

Server 在派发到 Execution 时把 `dataSourceId` 解析为 `DataSourceInfo`（含解密后的凭证）随 RPC 一起下发——Execution 不直接读 DB，凭证只在请求生命周期内存在。

## 安全建议

- `RUDDER_ENCRYPT_KEY` 与 `RUDDER_JWT_SECRET / RPC_AUTH_SECRET` **不要同值**
- 数据源密码不要复用其它系统密码
- 生产数据源使用最小权限账号（仅 SELECT / INSERT，不给 DDL）
- 限制网络层访问（数据源 host 只对 Execution 网段开放）
- 定期审计 `t_r_datasource_permission`，回收不再需要的授权
- `t_r_audit_log` 中会记录所有数据源 CRUD / 授权 / 测试连接操作

## 排障

| 症状 | 检查 |
|:---|:---|
| 测试连接报 `unknown driver` | classpath 缺驱动 jar；HIVE / TRINO / CLICKHOUSE 等驱动已包含在 bundle 中，确认 bundle 装配 |
| 报 `decrypt failed` | `RUDDER_ENCRYPT_KEY` 改了 → 用旧密钥解密、用新密钥加密重新保存所有数据源 |
| 任务运行 `Datasource not found` | 数据源被软删，或 workspace 没授权 |
| Trino 连接 OK 但元数据空 | Trino 需要 catalog 维度，确认 `database_name` 填的是 catalog 名 |
| 跨网络偶发超时 | 通过 `params` 加 `socketTimeout` / `connectTimeout` |

## 相关文档

- [权限模型](permissions.md) — 授权矩阵
- [配置参考](configuration.md#一密钥必填) — `RUDDER_ENCRYPT_KEY`
- [security/rotation.md](security/rotation.md) — 密钥轮换
- [AI 模块 - 知识库](ai/knowledge-base.md) — 数据源元数据如何喂给 AI
- [MCP](mcp.md) — 让 LLM 通过 `datasource.*` tools 浏览数据源（凭证脱敏）
