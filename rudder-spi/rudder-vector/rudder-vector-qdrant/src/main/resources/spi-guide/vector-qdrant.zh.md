---
description: Qdrant 向量存储,支持语义召回
---

## Qdrant 向量库接入(基于 Spring AI starter)

### 1. 部署 Qdrant
- Docker:`docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant`(本接入走 **6334 gRPC**)
- 托管:Qdrant Cloud

### 2. 填写参数
| 参数 | 值 |
| --- | --- |
| Host | Qdrant 主机,如 `127.0.0.1` / `qdrant.prod.svc` |
| gRPC Port | 默认 `6334`(本实现走 gRPC,不是 REST 6333) |
| Use TLS | Qdrant Cloud 填 `true`,私部通常 `false` |
| API Key | Qdrant Cloud 或启用 API_KEY 时填 |

### 3. 依赖链
`spring-ai-starter-vector-store-qdrant` 传递拉入 `io.qdrant:client`。
底层走官方 gRPC 客户端,collection 按 `{workspaceId}_{docType}` 动态建。
