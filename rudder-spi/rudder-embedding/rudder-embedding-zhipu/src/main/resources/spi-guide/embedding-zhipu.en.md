---
description: Zhipu AI native /api/paas/v4/embeddings (embedding-3)
---

## Zhipu AI Setup

### 1. Get an API Key
[open.bigmodel.cn](https://open.bigmodel.cn/) → API Keys. Format `xxx.yyy`.

### 2. Pick a model
| Model | Supported dimensions |
| --- | --- |
| `embedding-3` | 256 / 512 / 1024 / 2048 (default 2048; pass `dimensions` to reduce) |
| `embedding-2` | fixed 1024 |

### 3. Endpoint
The Zhipu embedding endpoint `POST /api/paas/v4/embeddings` is request/response-compatible with OpenAI.
Internally we reuse Spring AI `OpenAiEmbeddingModel` and only override `embeddingsPath`.
