---
description: Cohere-style REST API — Cohere / ZhipuAI / DashScope / Xinference / LocalAI
---

## Cohere-style Rerank Integration

Compatible with any "Cohere v2 compatible" rerank HTTP service. One SPI implementation, multiple providers — switch by changing endpoint + apiKey + model only.

| Provider | endpoint | model |
| --- | --- | --- |
| Cohere | `https://api.cohere.com/v2/rerank` | `rerank-v3.5` |
| ZhipuAI BigModel | `https://open.bigmodel.cn/api/paas/v4/rerank` | `rerank` |
| DashScope (compatible) | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` | `qwen3-rerank` |
| Xinference (self-hosted) | `http://<host>:9997/v1/rerank` | `bge-reranker-v2-m3`, etc. |
| LocalAI (self-hosted) | `http://<host>:8080/v1/rerank` | any loaded model |

### Configuration

- **apiKey**: required for cloud services; leave empty for self-hosted
- **endpoint**: full URL **including path** (not just domain)
- **model**: provider-specific model id

### Protocol

Request (shared by all providers):
```json
POST {endpoint}
{
  "model": "...",
  "query": "...",
  "documents": [...],
  "top_n": 5
}
```

Response (core fields):
```json
{
  "results": [
    {"index": 2, "relevance_score": 0.94},
    {"index": 0, "relevance_score": 0.61}
  ]
}
```

### Incompatible providers

DashScope's legacy endpoint (`gte-rerank-v2` / `qwen3-vl-rerank`) uses Aliyun's proprietary native format and **cannot be used via this SPI**. If needed, file an issue or contribute a separate `rudder-rerank-dashscope` SPI module.
