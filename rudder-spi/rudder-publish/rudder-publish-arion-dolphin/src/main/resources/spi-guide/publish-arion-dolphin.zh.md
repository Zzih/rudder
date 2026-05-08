---
description: 通过 Arion 网关发布到 DolphinScheduler
---

## Arion-Dolphin 发布接入指南

将 Rudder 的工作流定义、任务、脚本、调度推送至 DolphinScheduler。中间通过 Arion 网关代理转发，
Rudder 只与 Arion 通信，不直接访问 DolphinScheduler。

### 1. 准备 Arion 网关
1. 部署 Arion 网关服务，确保其可访问目标 DolphinScheduler 集群的 OpenAPI
2. 记录网关基础地址（形如 `http://arion.example.com` 或带端口 `http://10.0.0.1:12348`）
3. 若网关启用了鉴权，在网关侧创建调用方 Token 并记录

### 2. 同名规则
- **项目名**：Rudder 中的项目名将作为 DolphinScheduler 的 Project 名直接使用，
  发布时若 DS 中不存在同名 Project，Arion 会自动创建
- **工作流名 / 任务名**：作为 DS 中 ProcessDefinition / TaskDefinition 的名称直接使用，
  请保证在同一项目内唯一
- **用户名**：发布时使用当前操作用户的用户名作为 DS 用户，请提前在 DS 中创建对应用户

### 3. 任务类型映射
Rudder TaskType 在发布时按以下规则映射到 DS taskType：

| Rudder TaskType | DS taskType | 备注 |
|---|---|---|
| `HIVE_SQL` / `MYSQL` / `STARROCKS_SQL` / `TRINO_SQL` / `SPARK_SQL` / `FLINK_SQL` 等 SQL 类 | `SQL` | provider 自动补 `type` 字段（HIVE / MYSQL …），`dataSourceId` 重命名为 `datasource` |
| `SPARK_JAR` | `SPARK` | |
| `FLINK_JAR` | `FLINK` | |
| `PYTHON` / `SHELL` | `PYTHON` / `SHELL` | `content` 重命名为 `rawScript` |
| `HTTP` | `HTTP` | |
| `SEATUNNEL` | `SEATUNNEL` | |
| `CONDITION` | `CONDITIONS` | 控制流，taskParams 取自 `configJson` |
| `SUB_WORKFLOW` | `SUB_PROCESS` | |
| `SWITCH` | `SWITCH` | |
| `DEPENDENT` | `DEPENDENT` | |

DAG 边按 task name 转换为 DS 的 `TaskRelation`，无入边的节点 `preTaskName` 为空。

### 4. 调度配置
工作流若配置了调度（cron + 时区 + 起止时间），将一并下发到 DS：
- `cronExpression` 直接透传
- `timezone` 缺省时按 `Asia/Shanghai` 处理
- `startTime` / `endTime` 按 `yyyy-MM-dd HH:mm:ss` 格式化

未配置调度的工作流仅发布定义，不创建 DS 调度。

### 5. 数据源 ID 对齐
SQL 类任务的 `dataSourceId` 字段应与 DolphinScheduler 中已注册的 datasource ID 一致；
DS 端不存在该 ID 时，发布会失败。建议先在 DS 中创建 / 同步好数据源。

### 6. 填写配置
将下列信息填入字段后保存：
- **URL**：Arion 网关基础地址（必填）
- **Token**：Arion 鉴权 Token（如网关未启用鉴权可留空）

保存后，工作流发布走该 provider 推送，可在 DS Web UI 中查看发布结果。
