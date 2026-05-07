---
description: OpenAI /v1/embeddings compatible: OpenAI / DashScope / vLLM
---

## OpenAI-compatible Embedding

Same idea as the OpenAI LLM provider — works with any service that implements the OpenAI `/v1/embeddings` protocol.

| Service | baseUrl | model | dimensions |
| --- | --- | --- | --- |
| OpenAI | https://api.openai.com | text-embedding-3-small | 1536 |
| OpenAI | https://api.openai.com | text-embedding-3-large | 3072 |
| Aliyun DashScope | https://dashscope.aliyuncs.com/compatible-mode | text-embedding-v3 | 1024 |
| DeepSeek | embeddings not supported, pick another provider | — | — |
| Self-hosted vLLM | http://your-host:port | the embedding model you deployed | the model's dimension |

**Note**: `dimensions` must exactly match the model output. A mismatch will cause Qdrant collection creation to fail.
