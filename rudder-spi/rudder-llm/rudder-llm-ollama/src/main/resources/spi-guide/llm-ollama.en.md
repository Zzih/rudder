---
description: Local Ollama runtime, ideal for offline / private deployment
---

## Ollama (local LLM) Setup

Ollama is a local LLM runtime — no API key required, suited for offline or private-cloud deployments.

### 1. Install and start Ollama
- macOS: `brew install ollama && ollama serve`
- Linux: `curl -fsSL https://ollama.com/install.sh | sh`
- Docker: `docker run -d -p 11434:11434 ollama/ollama`

### 2. Pull a model
```
ollama pull llama3.1        # Meta Llama 3.1
ollama pull qwen2.5         # Tongyi Qianwen 2.5
ollama pull deepseek-r1     # DeepSeek R1
ollama pull mistral         # Mistral
```

### 3. Fill in the parameters
| Parameter | Value |
| --- | --- |
| Base URL | default `http://localhost:11434`; for remote, the Ollama server address |
| Model | a model you have pulled, e.g. `llama3.1` / `qwen2.5:14b` |
| Num Predict | max generated tokens, default `4096` |

### 4. Verify
After saving, test the AI chat from a workspace; the Ollama log will print each request.
