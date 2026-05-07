---
description: 智谱 AI 原生 /api/paas/v4/embeddings(embedding-3)
---

## 智谱 AI 接入

### 1. 获取 API Key
[open.bigmodel.cn](https://open.bigmodel.cn/) → 密钥管理。格式 `xxx.yyy`。

### 2. 模型选择
| 模型 | 支持维度 |
| --- | --- |
| `embedding-3` | 256 / 512 / 1024 / 2048(默认 2048,传 dimensions 参数降维) |
| `embedding-2` | 固定 1024 |

### 3. Endpoint
智谱 embedding 接口 `POST /api/paas/v4/embeddings` 请求/响应体与 OpenAI 兼容,
底层复用 Spring AI `OpenAiEmbeddingModel`,仅通过 `embeddingsPath` 覆写路径。
