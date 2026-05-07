---
description: OpenAI /v1/embeddings 协议兼容:OpenAI / DashScope / vLLM
---

## OpenAI 兼容 Embedding 接入

同 LLM provider,兼容所有实现 OpenAI `/v1/embeddings` 协议的服务。

| 服务 | baseUrl | model | dimensions |
| --- | --- | --- | --- |
| OpenAI | https://api.openai.com | text-embedding-3-small | 1536 |
| OpenAI | https://api.openai.com | text-embedding-3-large | 3072 |
| 阿里云百炼 | https://dashscope.aliyuncs.com/compatible-mode | text-embedding-v3 | 1024 |
| DeepSeek | 不支持 embedding,用其他 provider | — | — |
| 自建 vLLM | http://your-host:port | 你部署的 embedding 模型 | 对应模型维度 |

**注意**:dimensions 必须与模型输出维度严格一致。错配会导致 Qdrant collection 创建失败。
