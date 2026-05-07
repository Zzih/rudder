---
description: Query metadata directly through JDBC (no extra config)
---

## JDBC Metadata (default)

Reads `DatabaseMetaData` directly through the datasource connection pool. **No extra configuration required.**

### Characteristics

- Zero dependencies: no separate metadata service
- Real-time: queries the source for the latest metadata
- Universal: works with any JDBC-compatible source (MySQL / Hive / Spark / Trino / StarRocks / Flink)

### Cache

Shared in Redis for 5 minutes — multi-node consistency is automatic; one `invalidateCache` reaches every node.

### PII auto-classification

JDBC has **no concept of tags**, so column-level sensitivity labels can't be retrieved from the source.

Rudder falls back to its **built-in column-name classifier**: regex on column names —
`phone/mobile/email/id_card/password/api_key` → HIGH,
`full_name/address/birth` → MEDIUM. Custom column names have a high miss rate.

For accurate PII classification, use the OpenMetadata or DataHub provider.
