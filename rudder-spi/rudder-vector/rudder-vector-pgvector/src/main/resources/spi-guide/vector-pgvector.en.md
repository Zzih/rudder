---
description: PostgreSQL pgvector extension, reuse existing PG deployments
---

## PostgreSQL pgvector Setup (Spring AI starter)

### 1. Deploy
- Docker: `docker run -p 5432:5432 -e POSTGRES_PASSWORD=pg pgvector/pgvector:pg16`
- Managed: AWS RDS / GCP Cloud SQL — enable the `pgvector` extension

`CREATE EXTENSION IF NOT EXISTS vector` runs automatically on first write; needs superuser or CREATE permission.

### 2. Fill in the parameters
| Parameter | Value |
| --- | --- |
| Host | Postgres host |
| Port | default `5432` |
| Database | target DB name |
| Schema | schema for created tables, default `public` |
| Username / Password | DB credentials |

### 3. Dependency chain
`spring-ai-starter-vector-store-pgvector` transitively pulls `org.postgresql:postgresql`.
Collections are tables created dynamically as `{workspaceId}_{docType}`, using native `<=>` cosine distance + HNSW.
