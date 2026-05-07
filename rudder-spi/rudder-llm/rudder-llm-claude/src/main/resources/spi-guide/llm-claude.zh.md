---
description: Anthropic Claude 原生 messages API
---

## Claude 接入指南

### 1. 申请 API Key
- 官方渠道：前往 [Anthropic Console](https://console.anthropic.com/) 注册账号并在「API Keys」中创建
- 国内代理：若使用第三方代理（如 MiMo、OpenRouter 等），从代理商控制台获取对应的 API Key

### 2. 选择模型
常用模型标识：
- `claude-sonnet-4-6`（推荐，能力与成本平衡）
- `claude-opus-4-1`（最强能力）
- `claude-haiku-4-5`（低延迟低成本）

使用代理商时，请以代理方提供的 model 名称为准。

### 3. 配置 Base URL
- 官方：`https://api.anthropic.com`
- 代理：按代理商文档填写（通常以 `/anthropic` 结尾）

### 4. 保存
填入上述信息并保存后，AI 助手将立即启用。
