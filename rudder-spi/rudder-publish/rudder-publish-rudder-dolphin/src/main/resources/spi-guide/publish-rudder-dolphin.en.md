---
description: Publish to DolphinScheduler through the Rudder-Dolphin gateway
---

## Rudder-Dolphin Publish Setup

Pushes Rudder workflow definitions, tasks, scripts, and schedules to DolphinScheduler. Traffic is
proxied through the Rudder-Dolphin gateway; Rudder talks to Rudder-Dolphin only and never to DolphinScheduler directly.

### 1. Prepare the Rudder-Dolphin gateway
1. Deploy the Rudder-Dolphin gateway and ensure it can reach the target DolphinScheduler cluster's OpenAPI
2. Record the gateway base URL (e.g. `http://rudder-dolphin.example.com` or `http://10.0.0.1:12348`)
3. If the gateway enforces auth, mint a caller token there and record it

### 2. Naming rules
- **Project name**: the Rudder project name is used as-is for the DolphinScheduler project. Rudder-Dolphin
  auto-creates the project if it does not exist
- **Workflow / task name**: used as-is for the DS `ProcessDefinition` / `TaskDefinition` name. Keep
  them unique inside a project
- **User name**: the current operator's user name is sent as the DS user. Provision the matching
  user in DolphinScheduler beforehand

### 3. Task type mapping
Rudder `TaskType` maps to DS `taskType` as follows:

| Rudder TaskType | DS taskType | Notes |
|---|---|---|
| `HIVE_SQL` / `MYSQL` / `STARROCKS_SQL` / `TRINO_SQL` / `SPARK_SQL` / `FLINK_SQL` and other SQL kinds | `SQL` | Provider auto-fills the `type` field (HIVE / MYSQL …) and renames `dataSourceId` to `datasource` |
| `SPARK_JAR` | `SPARK` | |
| `FLINK_JAR` | `FLINK` | |
| `PYTHON` / `SHELL` | `PYTHON` / `SHELL` | `content` is renamed to `rawScript` |
| `HTTP` | `HTTP` | |
| `SEATUNNEL` | `SEATUNNEL` | |
| `CONDITION` | `CONDITIONS` | Control-flow node; `taskParams` comes from `configJson` |
| `SUB_WORKFLOW` | `SUB_PROCESS` | |
| `SWITCH` | `SWITCH` | |
| `DEPENDENT` | `DEPENDENT` | |

DAG edges are converted to DS `TaskRelation` entries by task name. Nodes with no incoming edge have
`preTaskName` set to null.

### 4. Schedule
If a schedule is configured (cron + timezone + start/end), it is published together with the
workflow:
- `cronExpression` is forwarded as-is
- `timezone` defaults to `Asia/Shanghai` when absent
- `startTime` / `endTime` are formatted as `yyyy-MM-dd HH:mm:ss`

Workflows without a schedule publish only the definition; no DS schedule is created.

### 5. Datasource ID alignment
The `dataSourceId` on SQL tasks must match an existing datasource in DolphinScheduler. Publishing
fails if the ID is unknown to DS. Provision or sync the datasource on the DS side first.

### 6. Fill in the configuration
Paste the following into the form and save:
- **URL**: Rudder-Dolphin gateway base URL (required)
- **Token**: Rudder-Dolphin bearer token (leave blank if the gateway is unauthenticated)

Once saved, workflow publishes go through this provider and the result can be inspected in the
DolphinScheduler web UI.
