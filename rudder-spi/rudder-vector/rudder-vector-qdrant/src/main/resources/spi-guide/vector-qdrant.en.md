---
description: Qdrant vector store, supports semantic recall
---

## Qdrant Setup (Spring AI starter)

### 1. Deploy Qdrant
- Docker: `docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant` (this provider uses **6334 gRPC**)
- Managed: Qdrant Cloud

### 2. Fill in the parameters
| Parameter | Value |
| --- | --- |
| Host | Qdrant host, e.g. `127.0.0.1` / `qdrant.prod.svc` |
| gRPC Port | default `6334` (this implementation uses gRPC, not REST 6333) |
| Use TLS | `true` for Qdrant Cloud, usually `false` for self-hosted |
| API Key | required for Qdrant Cloud or when API_KEY auth is enabled |

### 3. Dependency chain
`spring-ai-starter-vector-store-qdrant` transitively pulls `io.qdrant:client`.
Uses the official gRPC client; collections are created dynamically as `{workspaceId}_{docType}`.
