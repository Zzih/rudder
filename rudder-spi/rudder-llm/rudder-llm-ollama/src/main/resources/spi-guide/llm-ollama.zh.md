---
description: 本地 Ollama 运行时,适合离线 / 私部
---

## Ollama 本地 LLM 接入指南

Ollama 是一个本地化的大模型运行时,无需 API Key,适合离线 / 私域部署。

### 1. 安装并启动 Ollama
- macOS:`brew install ollama && ollama serve`
- Linux:`curl -fsSL https://ollama.com/install.sh | sh`
- Docker:`docker run -d -p 11434:11434 ollama/ollama`

### 2. 拉取模型
```
ollama pull llama3.1        # Meta Llama 3.1
ollama pull qwen2.5         # 通义千问 2.5
ollama pull deepseek-r1     # DeepSeek R1
ollama pull mistral         # Mistral
```

### 3. 填写参数
| 参数 | 值 |
| --- | --- |
| Base URL | 默认 `http://localhost:11434`,远程部署填 Ollama 服务器地址 |
| Model | 已 pull 的模型名,如 `llama3.1` / `qwen2.5:14b` |
| Num Predict | 生成 token 上限,默认 `4096` |

### 4. 验证
保存后可在工作空间里 AI 聊天测试,Ollama 日志也会打印每次请求。
