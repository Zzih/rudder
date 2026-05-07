---
description: Anthropic Claude native messages API
---

## Claude Setup

### 1. Get an API Key
- Official: register at the [Anthropic Console](https://console.anthropic.com/) and create one under "API Keys"
- Proxy providers: if you use a third-party proxy (MiMo, OpenRouter, etc.), get the API Key from the proxy's console

### 2. Pick a model
Common model IDs:
- `claude-sonnet-4-6` (recommended; balanced capability and cost)
- `claude-opus-4-1` (most capable)
- `claude-haiku-4-5` (low latency, low cost)

If you use a proxy, use the model name documented by that proxy.

### 3. Configure the Base URL
- Official: `https://api.anthropic.com`
- Proxy: follow the proxy's docs (usually ends with `/anthropic`)

### 4. Save
Once saved, the AI assistant becomes available immediately.
