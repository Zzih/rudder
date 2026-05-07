---
description: PostgreSQL pgvector 扩展,复用已有 PG 部署
---

## PostgreSQL pgvector 接入(基于 Spring AI starter)

### 1. 部署
- Docker:`docker run -p 5432:5432 -e POSTGRES_PASSWORD=pg pgvector/pgvector:pg16`
- 托管:AWS RDS / GCP Cloud SQL,需启用 `pgvector` 扩展

首次写入时会自动 `CREATE EXTENSION IF NOT EXISTS vector`,需 superuser 或 CREATE 权限。

### 2. 填写参数
| 参数 | 值 |
| --- | --- |
| Host | Postgres 主机 |
| Port | 默认 `5432` |
| Database | 目标库名 |
| Schema | 建表 schema,默认 `public` |
| Username / Password | DB 账号 |

### 3. 依赖链
`spring-ai-starter-vector-store-pgvector` 传递拉入 `org.postgresql:postgresql`。
collection 按 `{workspaceId}_{docType}` 动态建表,走原生 `<=>` 余弦距离 + HNSW。
