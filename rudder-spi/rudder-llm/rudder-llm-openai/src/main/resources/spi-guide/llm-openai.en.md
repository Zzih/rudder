---
description: OpenAI-compatible: OpenAI / DeepSeek / Qwen / Moonshot / vLLM / Ollama, etc.
---

## OpenAI-compatible Setup

This provider works with any service that implements the OpenAI `chat/completions` protocol —
including **OpenAI, DeepSeek, Moonshot, Qwen, Ollama, OpenRouter** and others.

### 1. Get an API Key
- OpenAI: [platform.openai.com](https://platform.openai.com/api-keys)
- DeepSeek: [platform.deepseek.com](https://platform.deepseek.com/)
- Moonshot: [platform.moonshot.cn](https://platform.moonshot.cn/)
- Other compatible services: see their own docs

### 2. Configure the Base URL
| Service | Base URL |
| --- | --- |
| OpenAI | `https://api.openai.com` |
| DeepSeek | `https://api.deepseek.com` |
| Moonshot | `https://api.moonshot.cn` |
| Ollama (local) | `http://localhost:11434` |

### 3. Model name
Use the name documented by your provider, e.g. `gpt-4o`, `deepseek-chat`, `moonshot-v1-128k`.

### 4. Save
Once saved, the AI assistant becomes available immediately.
