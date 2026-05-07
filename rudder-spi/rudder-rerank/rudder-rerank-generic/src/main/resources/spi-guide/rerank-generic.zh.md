---
description: Cohere 风格 REST 接口:Cohere / 智谱 / DashScope / Xinference / LocalAI 等
---

## Cohere 风格 Rerank 接入

兼容所有"Cohere v2 兼容"的 rerank HTTP 服务。一个 SPI 实现适配多个 provider,切换只改 endpoint + apiKey + model。

| Provider | endpoint | model |
| --- | --- | --- |
| Cohere 官方 | `https://api.cohere.com/v2/rerank` | `rerank-v3.5` |
| 智谱 BigModel | `https://open.bigmodel.cn/api/paas/v4/rerank` | `rerank` |
| DashScope 兼容接口 | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` | `qwen3-rerank` |
| Xinference 自部署 | `http://<host>:9997/v1/rerank` | `bge-reranker-v2-m3` 等 |
| LocalAI 自部署 | `http://<host>:8080/v1/rerank` | 任意已加载模型 |

### 配置说明

- **apiKey**:云服务必填;自部署服务可空
- **endpoint**:完整 URL,**包含路径**(不只是域名)
- **model**:对应 provider 的具体模型 id

### 协议规格

请求(所有 provider 共用):
```json
POST {endpoint}
{
  "model": "...",
  "query": "...",
  "documents": [...],
  "top_n": 5
}
```

响应(核心字段):
```json
{
  "results": [
    {"index": 2, "relevance_score": 0.94},
    {"index": 0, "relevance_score": 0.61}
  ]
}
```

### 不兼容的 provider

DashScope 老接口(`gte-rerank-v2` / `qwen3-vl-rerank`)用阿里专有 native 协议,**不能用本 SPI 接入**。如有需求请提 issue 或新建 `rudder-rerank-dashscope` SPI 模块。
