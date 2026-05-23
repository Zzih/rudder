---
description: 数据权限平台配置说明
---

## 概念

Rudder 数据权限提供两种独立鉴权模式,可单独或同时启用。

- **Ranger 同步** — Reconciler 把申请通过的权限推送到 Apache Ranger,由各引擎的 Ranger Plugin 在查询时拦截。适用于已有 Ranger 基础设施的部署。
- **本地鉴权** — Worker 在执行 SQL 前解析待访问的表 / 操作,查内置权限快照表比对;缺权直接拒绝任务执行。适用于无 Ranger 或希望增加一层 Rudder 侧拦截的部署。
- **数据权限域 (Scope)** — 受 Rudder 管控的逻辑域,定义元数据来源与受 Local 鉴权拦截的任务类型集合;启用 Ranger 同步时同步对应到 Ranger Service。权限包与申请单均以 Scope 为单位组织资源路径与 access。
- **权限事实快照** — 每轮 Reconciler 算出当前所有用户的事实权限,与最近版本对比有变化则写新版本。Local 鉴权直接消费快照,Ranger 同步以快照为对账源。

> 同一类 TaskType 应由唯一 Scope 管控。例如 HADOOP_SQL Scope 可同时声明覆盖 `HIVE_SQL` 与 `SPARK_SQL`(含上云 Serverless Spark),实现"一份权限多个引擎入口生效"。

## 启用模式

1. **总开关** 打开后,至少需启用 Ranger 同步与本地鉴权之一,否则保存校验失败。
2. **仅本地鉴权** — 不依赖任何外部 Ranger 服务;Worker 执行 SQL 前查快照表,缺权拒绝任务。适合开箱即用与轻量部署。
3. **仅 Ranger 同步** — Rudder 仅作权限编辑与申请审批入口,enforce 完全在 Ranger 端。适合已有 Ranger 基础设施且不希望 Rudder 侧拦截的场景。
4. **两者同时启用** — Local 鉴权先在任务侧拒绝缺权请求,Ranger 同步保障引擎侧二次防护;两层独立解耦,任一不可用不会阻塞另一层。

## 配置流程

1. **开启总开关**,在「鉴权模式」区按需选择 Ranger 同步 / 本地鉴权。
2. **登记数据权限域** — 在「数据权限域」区新增一个或多个 Scope。
   - 必填:名称、PluginType、元数据数据源。
   - **本地鉴权启用时**:为受管控的 Scope 配置「受管任务类型」(可多选),只有这些 TaskType 的任务会触发 Local 鉴权;TaskType 跨 Scope 不能重复绑定。
   - **Ranger 同步启用时**:为 Scope 填写「Ranger Service 名」,值需与 Ranger Admin 中已建的 service.name 一致,Reconciler 按此名写 policy。
3. **填 Ranger Admin 接入信息**(仅 Ranger 同步开启时显示):Admin URL + 用户名 + 密码,保存前可点击「测试连通性」验证。
4. **同步策略** — 调整 Reconciler 周期(对两种鉴权模式通用);写并发与失败告警阈值仅 Ranger 同步使用。
5. **使用** — 用户进「资源包管理」/「申请权限」按 Scope 维度配置资源路径与 access;审批通过后由 Reconciler 周期写入,审批 / 撤销时**立即额外触发**一次。

## Plugin Type 对照

| Plugin Type | 对应引擎 | 资源层级 |
|---|---|---|
| `HADOOP_SQL` | Hive / Spark Thrift / Impala(共用 Hive Ranger plugin) | database / table / column |
| `STARROCKS` | StarRocks 3.1.9+(原生 plugin) | catalog / database / table / column |
| `TRINO` | Trino(原生 plugin) | catalog / schema / table / column |
| `HBASE` / `HDFS` / `KAFKA` | 预留扩展 | —— |

## 字段释义

- **Admin URL** — Ranger Admin REST endpoint,例 `http://ranger-admin:6080`。仅 Ranger 同步使用。
- **Reconciler 周期(秒)** — 周期轮询间隔,建议 ≥ 300。两种鉴权模式通用:本地鉴权用于刷新快照,Ranger 同步用于推送 policy。审批 / 撤销额外即时触发。
- **并发写入** — Ranger Admin 写入线程池容量,建议 4-8;过高可能压垮 Admin。仅 Ranger 同步使用。
- **失败告警阈值** — 连续 N 轮整体失败后告警,避免抖动误报。仅 Ranger 同步使用。
- **元数据数据源 (Metadata)** — 资源包 / 申请页拉取 db / table / column 下拉时使用的连接,每个 Scope 选一个。
- **受管任务类型 (Managed)** — Worker 执行这些 TaskType 的任务时触发该 Scope 的本地鉴权;TaskType 跨 Scope 不能重复绑定。
- **Ranger Service 名** — 必须与 Ranger Admin 已建的 service.name 一致,Reconciler 据此拼 policy URL。仅 Ranger 同步开启时必填。
- **Scope Enabled** — 关闭后 Reconciler 跳过该 Scope 下所有 desired 项,**已写入 Ranger 的 policy 不会自动清除**。

## 本地鉴权工作流

1. Worker 接收实现 `DataPermAwareTask` 的任务后,在调用底层执行入口前解析 pre / main / post 三段 SQL,产出"待访问的资源 + 操作"列表。
2. 取任务的 TaskType (来自 ctx.taskType) + 任务发起人 user_id (= task_instance.created_by),按 TaskType 反查 Scope (任一启用 Scope 的 `managedTaskTypes` 包含该 TaskType 即命中)。
3. 未匹配任何 Scope → 放行 (该任务类型不受管控)。
4. 命中 Scope → 查询该用户在该 Scope 下的权限事实快照,逐项比对操作权限。
5. 任一项缺权 → 抛出 `LOCAL_AUTH_DENIED` 错误,任务标记失败并附缺权清单。

**SQL 解析失败时**采用 fail-open 策略,放行该任务;Local 鉴权仅作"已识别意图"的强校验,引擎侧通过 Ranger 兜底或人工 Review 兜底。

## 常见问题

- **保存提示"启用数据权限须至少开启 Ranger 同步或本地鉴权之一"** — 总开关开但两个 mode 全关,选择至少一个 mode 启用。
- **保存提示"启用本地鉴权须至少为一个启用的权限域配置受管任务类型"** — Local mode 开但所有启用 Scope 的 managedTaskTypes 都为空,至少为一个 Scope 绑定 TaskType。
- **保存提示"任务类型 X 已被其他权限域管控,不能重复绑定"** — 同一 TaskType 不能跨 Scope 绑定,先解除之前的绑定。
- **启用 Ranger 同步后无效** — 查看 reconcile 日志确认定时执行;右下「测试连通性」核对 Admin URL / 凭证可达。
- **本地鉴权未拦截预期任务** — 检查该任务的 TaskType 是否在某个 enabled Scope 的「受管任务类型」中;若 SQL 解析失败 fail-open 也会放行,详见 reconcile / worker 日志。
- **`Ranger service not found: <name>`** — 对应 PluginType 的 service-def 未在 Ranger Admin 注册,按下文「附录」补装。
- **policy 已写但 Ranger plugin 未拦截** — 检查被治理服务端 plugin 配置的 `service.name` 与本页是否一致;plugin 缓存默认 30 秒。
- **删除 Scope 报 "in use"** — 还有资源包 / Direct 授权引用,先到「资源包管理」清理引用再删。
- **删除 Datasource 报 "metadata in use"** — 该 datasource 被某 Scope 作为 metadataDatasource 引用,先在本页解除关联或删除该 Scope。

## 附录:Ranger service-def 安装

Ranger Admin 启动时按 `ranger.supportedcomponents` 配置(默认见 `EmbeddedServiceDefsUtil.DEFAULT_BOOTSTRAP_SERVICEDEF_LIST`)自动加载内置 service-def。**hive / trino / hdfs / hbase / kafka 等都在默认列表里**,无需手动安装;**starrocks 等第三方 service-def 不在列表里**,需要走 REST API 注册。

### 检查 Ranger Admin 已注册的 service-def

```bash
RANGER_URL=http://ranger-admin:6080
RANGER_AUTH=admin:<password>

curl -u "$RANGER_AUTH" "$RANGER_URL/service/public/v2/api/servicedef" \
  | jq '[.serviceDefs[].name]'
```

输出中已含的 service-def 直接复用;缺的按下面流程注册。

### 注册缺失的 service-def

> Ranger Admin Web UI 不提供 service-def 导入入口;Ranger plugin 安装脚本(`enable-{engine}-plugin.sh`)仅配置 plugin 侧 jar/xml,不注册 service-def。**注册唯一通道是 REST API**。

#### 1. 下载官方 service-def JSON

| PluginType | 是否默认 bootstrap | service-def 来源(缺时下载) |
|---|---|---|
| `HADOOP_SQL`(hive) | ✓ Apache Ranger 默认已带 | 无需下载 |
| `TRINO` | ✓ 默认 bootstrap 列表已含 | `apache/ranger/agents-common/.../service-defs/ranger-servicedef-trino.json` |
| `STARROCKS` | ✗ 不在默认列表 | `https://raw.githubusercontent.com/StarRocks/starrocks/main/conf/ranger/ranger-servicedef-starrocks.json` |

```bash
curl -sSL -o /tmp/sd-starrocks.json \
  "https://raw.githubusercontent.com/StarRocks/starrocks/main/conf/ranger/ranger-servicedef-starrocks.json"
```

#### 2. POST 注册到 Ranger Admin

```bash
curl -u "$RANGER_AUTH" \
  -H "Content-Type: application/json" \
  -X POST \
  -d @/tmp/sd-starrocks.json \
  "$RANGER_URL/service/public/v2/api/servicedef"
```

#### 3. 验证

```bash
curl -u "$RANGER_AUTH" "$RANGER_URL/service/public/v2/api/servicedef" \
  | jq '[.serviceDefs[].name]'
```

#### 4. Rudder 端生效

执行「平台管理 → 数据权限配置 → 保存」,会清掉 service-def 缓存,下次访问从 Ranger Admin 重拉。

### 注意

- **重复注册返 400** — 同名 service-def 不可重复 POST。需重装时先 `DELETE /service/public/v2/api/servicedef/name/<name>`,该操作会一并删除该 service-def 下所有 service 与 policy,生产环境慎用。
- **trino 与 presto** — trino 由 presto 改名而来。若 Ranger Admin 已装老的 `presto` service-def,trino 可作为独立 service-def 共存,两者互不影响。
- **service-def `id` / `version` 字段** — 官方 JSON 默认带,Ranger Admin 多数版本能容忍;若 POST 报错,去掉这两个字段重试。
