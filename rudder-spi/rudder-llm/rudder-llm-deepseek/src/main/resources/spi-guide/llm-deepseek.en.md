---
description: DeepSeek native API (not the OpenAI-compatible mode)
---

## DeepSeek Setup

### 1. Get an API Key
Sign up at the [DeepSeek platform](https://platform.deepseek.com/) and create one under "API Keys".

### 2. Available models
- `deepseek-chat` (general chat, recommended)
- `deepseek-reasoner` (reasoning mode)

### 3. Base URL
Default `https://api.deepseek.com`; replace if you run a self-hosted proxy.

### 4. Differences vs the OpenAI protocol
DeepSeek is OpenAI-compatible too, but this provider uses Spring AI's official `DeepSeekChatModel`,
which exposes DeepSeek-only parameters (e.g. reasoner thinking mode). For plain chat you can also pick the OPENAI provider in compatibility mode.
