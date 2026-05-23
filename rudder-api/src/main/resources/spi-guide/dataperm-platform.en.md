---
description: Data Permission Platform Configuration Guide
---

## Concepts

Rudder's data permission offers two independent enforcement modes that may be enabled separately or together.

- **Ranger Sync** — The Reconciler pushes approved grants to Apache Ranger; each engine's Ranger plugin intercepts queries at the source. Use when an existing Ranger deployment is in place.
- **Local Enforcement** — The Worker parses SQL before execution, derives the tables / operations involved, and checks them against an internal permission snapshot. Missing grants reject the task immediately. Use when no Ranger deployment exists, or to add an extra Rudder-side gate.
- **Data Permission Scope** — A managed logical domain that defines a metadata source plus the task types subject to local enforcement; when Ranger Sync is on, the scope is paired with a Ranger service. All role permissions and applications are organised under scopes.
- **Permission Effective Snapshot** — Every Reconciler round computes the current effective grants for all users and writes a new version when the snapshot diverges from the previous one. Local enforcement reads the snapshot directly; Ranger sync reconciles against the same snapshot.

> A single TaskType must be governed by at most one Scope. A HADOOP_SQL Scope may declare both `HIVE_SQL` and `SPARK_SQL` (including cloud Serverless Spark) under its `managedTaskTypes`, applying one grant set to multiple engine entrypoints.

## Enabling Modes

1. With the master toggle on, **at least one** of Ranger Sync or Local Enforcement must be enabled, otherwise saving is rejected.
2. **Local Enforcement only** — No external Ranger required. Worker checks the snapshot before executing SQL and rejects missing grants. Suitable for out-of-the-box and lightweight deployments.
3. **Ranger Sync only** — Rudder acts purely as the grant editor / approval portal; enforcement lives in Ranger. Suitable for sites with existing Ranger infrastructure.
4. **Both enabled** — Local enforcement rejects at task submit time; Ranger sync provides defence in depth at the engine layer. The two layers are decoupled — either being unavailable does not block the other.

## Configuration Flow

1. **Turn on the master toggle**, then select Ranger Sync / Local Enforcement under "Enforcement Modes".
2. **Register data permission scopes** under "Data Permission Scopes".
   - Required: name, plugin type, metadata datasource.
   - **When Local Enforcement is on**: configure "Managed Task Types" (multi-select) on each managed scope; only tasks of these types trigger local enforcement. A TaskType cannot be bound across multiple scopes.
   - **When Ranger Sync is on**: fill in "Ranger Service Name" matching a service.name pre-created in Ranger Admin; the Reconciler writes policies against this name.
3. **Fill in Ranger Admin credentials** (visible only when Ranger Sync is on): admin URL, username, password. Use "Test Connection" to verify before saving.
4. **Sync strategy** — Adjust the Reconciler interval (applies to both modes). Write concurrency and alert threshold are Ranger-only.
5. **Use it** — Users go to "Bundles" / "Apply for Permissions" to configure resource paths and actions under each scope; approved grants are written by the Reconciler periodically, with an extra immediate trigger on approval / revoke.

## Plugin Type Map

| Plugin Type | Engine | Resource Hierarchy |
|---|---|---|
| `HADOOP_SQL` | Hive / Spark Thrift / Impala (shared Hive Ranger plugin) | database / table / column |
| `STARROCKS` | StarRocks 3.1.9+ (native plugin) | catalog / database / table / column |
| `TRINO` | Trino (native plugin) | catalog / schema / table / column |
| `HBASE` / `HDFS` / `KAFKA` | Reserved | —— |

## Field Reference

- **Admin URL** — Ranger Admin REST endpoint, e.g. `http://ranger-admin:6080`. Ranger Sync only.
- **Reconciler Interval (seconds)** — Periodic polling interval, recommended ≥ 300. Applies to both modes: refresh snapshot for Local, push policies for Ranger. Approval / revoke triggers an additional immediate run.
- **Write Concurrency** — Ranger Admin write thread pool size, recommended 4-8; higher values may overload Admin. Ranger Sync only.
- **Failure Alert Threshold** — Alert after N consecutive failed rounds, avoids false alarms from flaky requests. Ranger Sync only.
- **Metadata Datasource** — The connection used by the role / apply UI to enumerate db / table / column. One per scope.
- **Managed Task Types** — Worker triggers this scope's local enforcement when executing tasks of these types; the same TaskType cannot be bound across multiple scopes.
- **Ranger Service Name** — Must match a service.name pre-created in Ranger Admin; the Reconciler writes policy URLs against it. Required when Ranger Sync is on.
- **Scope Enabled** — When off, the Reconciler skips all desired items under this scope; already-written Ranger policies are not automatically cleaned up.

## Local Enforcement Flow

1. When the Worker receives a task implementing `DataPermAwareTask`, before opening the execution channel it parses the pre / main / post SQL segments and produces a list of "resource + action" intents.
2. It takes the task's TaskType (from `ctx.taskType`) and submitter user_id (= `task_instance.created_by`), then looks up the owning Scope by `managedTaskTypes` containing the TaskType.
3. No matching Scope → allow (task type is unmanaged).
4. Match found → query the user's effective snapshot in this scope and check each intent against granted actions.
5. Any missing action → throws `LOCAL_AUTH_DENIED`; the task is marked failed with a list of missing grants.

**SQL parse failure** uses fail-open: the task is allowed to proceed. Local enforcement is a strict check on recognised intents; coverage of unrecognised syntax is handled by Ranger or human review at the engine layer.

## FAQ

- **Save fails with "at least one mode required"** — Master toggle is on but both modes are off. Enable at least one mode.
- **Save fails with "local enforcement requires managed task types"** — Local mode is on but no enabled scope has managed task types. Bind a TaskType to a scope.
- **Save fails with "task type X is already governed by another scope"** — A TaskType cannot be bound to multiple scopes; release the previous binding first.
- **Ranger Sync enabled but ineffective** — Check the reconcile log for periodic execution; use "Test Connection" to verify Admin URL / credentials.
- **Local enforcement did not block the expected task** — Check whether the task's TaskType appears under some enabled scope's "Managed Task Types"; SQL parse failure fail-opens, see reconcile / worker logs for details.
- **`Ranger service not found: <name>`** — The matching plugin-type service-def is not registered on Ranger Admin. See the appendix below.
- **Policy written but Ranger plugin does not block** — Check that the engine plugin's `service.name` config matches what is here; plugin cache defaults to 30 seconds.
- **Delete Scope reports "in use"** — A role permission or direct grant still references it. Clear references under "Bundles" first.
- **Delete Datasource reports "metadata in use"** — The datasource is referenced by some scope as its metadata datasource. Detach it on this page or delete the owning scope first.

## Appendix: Ranger service-def Installation

On startup Ranger Admin loads bootstrap service-defs per the `ranger.supportedcomponents` setting (default list at `EmbeddedServiceDefsUtil.DEFAULT_BOOTSTRAP_SERVICEDEF_LIST`). **hive / trino / hdfs / hbase / kafka** are included by default; **starrocks** and other third-party defs must be registered via REST API.

### Inspect Currently Registered service-defs

```bash
RANGER_URL=http://ranger-admin:6080
RANGER_AUTH=admin:<password>

curl -u "$RANGER_AUTH" "$RANGER_URL/service/public/v2/api/servicedef" \
  | jq '[.serviceDefs[].name]'
```

Already-registered defs may be reused directly; missing ones follow the steps below.

### Register a Missing service-def

> Ranger Admin's Web UI provides no service-def import entry. The `enable-{engine}-plugin.sh` scripts only deploy plugin jars/configs and do not register service-defs. **REST API is the only channel.**

#### 1. Download the official service-def JSON

| PluginType | Bootstrapped by default | service-def source (when missing) |
|---|---|---|
| `HADOOP_SQL` (hive) | ✓ Bundled with Apache Ranger | Not needed |
| `TRINO` | ✓ Included in default bootstrap list | `apache/ranger/agents-common/.../service-defs/ranger-servicedef-trino.json` |
| `STARROCKS` | ✗ Not in default list | `https://raw.githubusercontent.com/StarRocks/starrocks/main/conf/ranger/ranger-servicedef-starrocks.json` |

```bash
curl -sSL -o /tmp/sd-starrocks.json \
  "https://raw.githubusercontent.com/StarRocks/starrocks/main/conf/ranger/ranger-servicedef-starrocks.json"
```

#### 2. POST to Ranger Admin

```bash
curl -u "$RANGER_AUTH" \
  -H "Content-Type: application/json" \
  -X POST \
  -d @/tmp/sd-starrocks.json \
  "$RANGER_URL/service/public/v2/api/servicedef"
```

#### 3. Verify

```bash
curl -u "$RANGER_AUTH" "$RANGER_URL/service/public/v2/api/servicedef" \
  | jq '[.serviceDefs[].name]'
```

#### 4. Activate on Rudder Side

Hit "Platform Admin → Data Permission Configuration → Save"; the service-def cache is invalidated and refetched from Ranger Admin on the next call.

### Notes

- **Duplicate registration returns 400** — Same-name service-defs cannot be re-POSTed. To reinstall, `DELETE /service/public/v2/api/servicedef/name/<name>` first; that also deletes all services and policies under that def — use with care in production.
- **trino vs presto** — trino was renamed from presto. If Ranger Admin already has the legacy `presto` service-def, the `trino` def can coexist independently.
- **service-def `id` / `version` fields** — Most Ranger Admin versions tolerate the official JSON's defaults; on POST failure, strip these two fields and retry.
