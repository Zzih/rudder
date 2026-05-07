---
description: DeepSeek 原生 API(非 OpenAI 兼容模式)
---

## DeepSeek 接入指南

### 1. 申请 API Key
前往 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册,在「API Keys」创建。

### 2. 可选模型
- `deepseek-chat`(通用对话,推荐)
- `deepseek-reasoner`(推理模式)

### 3. Base URL
默认 `https://api.deepseek.com`,自建代理可替换。

### 4. 与 OpenAI 协议的区别
虽然 DeepSeek 也兼容 OpenAI 协议,本 provider 走 Spring AI 官方 `DeepSeekChatModel`,
可用 DeepSeek 独家参数(如 reasoner 思考模式)。仅要通用对话也可直接选 OPENAI provider 走兼容模式。
