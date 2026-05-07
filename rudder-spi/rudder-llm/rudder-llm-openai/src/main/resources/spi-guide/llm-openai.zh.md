---
description: OpenAI 协议兼容:OpenAI / DeepSeek / Qwen / Moonshot / vLLM / Ollama 等
---

## OpenAI 兼容接入指南

本 Provider 兼容所有实现了 OpenAI `chat/completions` 协议的服务，
包括 **OpenAI、DeepSeek、Moonshot、Qwen、Ollama、OpenRouter** 等。

### 1. 获取 API Key
- OpenAI：[platform.openai.com](https://platform.openai.com/api-keys)
- DeepSeek：[platform.deepseek.com](https://platform.deepseek.com/)
- Moonshot：[platform.moonshot.cn](https://platform.moonshot.cn/)
- 其他兼容服务按各自官网文档获取

### 2. 配置 Base URL
| 服务 | Base URL |
| --- | --- |
| OpenAI | `https://api.openai.com` |
| DeepSeek | `https://api.deepseek.com` |
| Moonshot | `https://api.moonshot.cn` |
| Ollama（本地） | `http://localhost:11434` |

### 3. 模型名称
按服务商文档填写，如 `gpt-4o`、`deepseek-chat`、`moonshot-v1-128k` 等。

### 4. 保存
填入上述信息并保存后，AI 助手将立即启用。
