---
description: Centralized metadata via DataHub GraphQL API
---

## DataHub Metadata

Use [DataHub](https://datahubproject.io/) as a central metadata store. v0.12+ recommended.

### Connection

1. **URL**: DataHub GMS address, e.g. `http://datahub-gms:8080`
2. **Access Token**: DataHub → Settings → Access Tokens → Generate Personal Access Token
3. **Environment**: fabric type, usually `PROD`; participates in dataset URNs

### Datasource binding (required)

Each Rudder datasource maps to one `platform_instance` in DataHub. In your ingestion recipe, add:

```yaml
source:
  type: hive            # or mysql / trino / ...
  config:
    platform_instance: <Rudder datasource name, exactly>
```

- Without it → all Rudder datasources of the same engine **share** every dataset in DataHub; no isolation
- `platform_instance` is part of the URN, so a Rudder **datasource name cannot be renamed** after creation

### PII auto-classification (optional)

When enabled, Rudder infers PII level from DataHub column-level tags so masking rules can be applied per level
(e.g. `PII_LEVEL=HIGH → REPLACE`).

DataHub does **not** ship a PII glossary; you need to:

1. DataHub → Govern → Tags / Glossary, define terms such as `PII.HIGH` / `PII.MEDIUM`
2. In each ingestion recipe, attach a transformer that tags columns by name regex:

   ```yaml
   transformers:
     - type: "pattern_add_dataset_schema_tags"
       config:
         tag_pattern:
           rules:
             ".*(phone|email|id_?card|password|token|secret).*":
               - "urn:li:tag:PII.HIGH"
             ".*(name|address|birth).*":
               - "urn:li:tag:PII.MEDIUM"
   ```

If not configured → Rudder falls back to its **built-in column-name classifier** (covers common fields like
`phone/email/id_card`; custom column names have a high miss rate).
