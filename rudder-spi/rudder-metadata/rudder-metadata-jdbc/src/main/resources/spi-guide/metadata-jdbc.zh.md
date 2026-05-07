---
description: 通过 JDBC 连接直接查询元数据(无需额外配置)
---

## JDBC 元数据(默认)

直接走数据源连接池查 `DatabaseMetaData`,**无需额外配置**。

### 特点

- 零依赖:不需要额外元数据服务
- 实时性:直接查数据源拿最新元数据
- 通用性:支持所有 JDBC 兼容数据源(MySQL / Hive / Spark / Trino / StarRocks / Flink)

### 缓存

Redis 共享缓存 5 分钟,多节点天然一致;`invalidateCache` 一次全节点生效。

### PII 自动识别

JDBC 协议**无 tag 概念**,无法从数据源拿到列级敏感标签。

Rudder 只能退化到**内置列名分类器**:按列名正则匹配
`phone/mobile/email/id_card/password/api_key` → HIGH,
`full_name/address/birth` → MEDIUM。自定义列名漏判率高。

如需精准 PII 识别,换 OpenMetadata / DataHub provider。
