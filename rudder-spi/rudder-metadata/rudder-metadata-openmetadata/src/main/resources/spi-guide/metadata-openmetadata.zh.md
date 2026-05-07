---
description: 通过 OpenMetadata REST API 集中管理元数据(内置 PII 分类)
---

## OpenMetadata 元数据

用 [OpenMetadata](https://open-metadata.org/) 做集中式元数据。推荐 1.5+。

### 连接

1. **URL**:OM 地址,如 `http://openmetadata:8585`
2. **JWT Token**:OM → Settings → Bots → 选 bot(如 `ingestion-bot`)→ Generate New Token

### 数据源绑定(必需)

Rudder 数据源 name **必须**等于 OM 里 Database Service name。

配置路径:OM → Settings → Services → Databases → Add New Service,
"Service Name" 与 Rudder 该数据源完全一致。

- 不一致 → 查不到 service,返回空元数据(**无降级**,OM API 天然按 service 隔离)
- Service name 参与 FQN,**Rudder 数据源 name 创建后不可改**

### 层级映射

OM 固定 `Service → Database → Schema → Table`,Rudder 两种引擎对齐:

- **2 层**(Hive / MySQL / Spark / Flink):`Rudder.database` → `OM.Database`,Schema 取第一个
- **3 层**(Trino / StarRocks):`Rudder.catalog` → `OM.Database`,`Rudder.database` → `OM.Schema`

### PII 自动识别(开箱即用)

开启后 Rudder 按 OM 列级 tag 推断 PII 级别,脱敏规则可按级别一刀切
(如 `PII_LEVEL=HIGH → REPLACE`)。

OM **内置 PII Classification**,admin 两种方式给列打标:

1. **Auto Classification**(推荐):
   OM → Settings → Applications → Auto Classification → Configure → 跑调度 ingestion,
   自动按正则给列打 `PII.Sensitive / PII.NonSensitive / PII.None`
2. **手动**:表详情 → 列 → Edit Tags → 选 `PII.Sensitive` 等

Rudder 侧固定映射:`PII.Sensitive → HIGH` / `PII.NonSensitive → MEDIUM` / `PII.None → KEEP`。
不配置 → 退化到**内置列名分类器**。
