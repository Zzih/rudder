---
description: 通过 DataHub GraphQL API 集中管理元数据
---

## DataHub 元数据

用 [DataHub](https://datahubproject.io/) 做集中式元数据。推荐 v0.12+。

### 连接

1. **URL**:DataHub GMS 地址,如 `http://datahub-gms:8080`
2. **Access Token**:DataHub → Settings → Access Tokens → Generate Personal Access Token
3. **Environment**:fabric type,一般 `PROD`,参与 dataset URN

### 数据源绑定(必需)

每个 Rudder 数据源对应 DataHub 里一个 `platform_instance`。在 ingestion recipe 加:

```yaml
source:
  type: hive            # 或 mysql / trino / ...
  config:
    platform_instance: <Rudder 里该数据源 name,完全一致>
```

- 不加 → 所有同类型 Rudder 数据源**共享** DataHub 全量 dataset,无法隔离
- `platform_instance` 写入 URN,**Rudder 数据源 name 创建后不可改**

### PII 自动识别(可选)

开启后 Rudder 按 DataHub 列级 tag 推断 PII 级别,脱敏规则可按级别一刀切
(如 `PII_LEVEL=HIGH → REPLACE`)。

DataHub **不内置 PII 词表**,需要:

1. DataHub → Govern → Tags / Glossary 建词表,约定如 `PII.HIGH` / `PII.MEDIUM`
2. 每个 ingestion recipe 挂 transformer 按列名正则打标:

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

不配置 → Rudder 退化到**内置列名分类器**(覆盖 `phone/email/id_card` 等常见字段,
自定义列名漏判率高)。
