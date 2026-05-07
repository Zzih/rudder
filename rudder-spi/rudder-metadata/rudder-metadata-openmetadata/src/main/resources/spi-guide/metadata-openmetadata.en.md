---
description: Centralized metadata via OpenMetadata REST API (built-in PII classification)
---

## OpenMetadata Metadata

Use [OpenMetadata](https://open-metadata.org/) as a central metadata store. 1.5+ recommended.

### Connection

1. **URL**: OM address, e.g. `http://openmetadata:8585`
2. **JWT Token**: OM → Settings → Bots → pick a bot (e.g. `ingestion-bot`) → Generate New Token

### Datasource binding (required)

The Rudder datasource name **must** equal the Database Service name in OM.

OM → Settings → Services → Databases → Add New Service; "Service Name" must match the Rudder datasource exactly.

- Mismatch → service not found, returns empty metadata (**no fallback**, OM API is service-scoped by design)
- Service name participates in the FQN, so the Rudder **datasource name cannot be renamed** after creation

### Hierarchy mapping

OM uses a fixed `Service → Database → Schema → Table`. Rudder aligns the two engine families:

- **2-level** (Hive / MySQL / Spark / Flink): `Rudder.database` → `OM.Database`, Schema = first one
- **3-level** (Trino / StarRocks): `Rudder.catalog` → `OM.Database`, `Rudder.database` → `OM.Schema`

### PII auto-classification (out of the box)

When enabled, Rudder infers PII level from OM column-level tags so masking rules can be applied per level
(e.g. `PII_LEVEL=HIGH → REPLACE`).

OM ships a **built-in PII Classification**. Admins can label columns in two ways:

1. **Auto Classification** (recommended):
   OM → Settings → Applications → Auto Classification → Configure → run scheduled ingestion;
   columns are labelled `PII.Sensitive / PII.NonSensitive / PII.None` automatically by regex
2. **Manual**: table detail → column → Edit Tags → choose `PII.Sensitive`, etc.

Rudder mapping is fixed: `PII.Sensitive → HIGH` / `PII.NonSensitive → MEDIUM` / `PII.None → KEEP`.
If not configured → falls back to the **built-in column-name classifier**.
