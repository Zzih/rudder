---
description: MCP Inspector — Anthropic 官方调试器，浏览 tool / 触发调用 / 看实时响应
---

`@modelcontextprotocol/inspector` 是 Anthropic 官方提供的 MCP server 调试器，正确处理 Streamable HTTP / SSE 传输、协议握手、流式响应——比裸 curl 靠谱得多，是接 MCP 命令行排障的标准做法。

### 启动

```bash
npx @modelcontextprotocol/inspector
```

执行后会自动开浏览器到 `http://localhost:6274`（端口可变）。左侧表单填：

- **Transport Type**：`Streamable HTTP`
- **URL**：`{{baseUrl}}`
- **Authentication**：勾 Bearer Token，填 PAT 明文（`rdr_pat_...`）

点 **Connect**，左下角 `Tools` 面板会列出所有可用工具。点任一工具填参数 → `Run Tool`，右侧实时看 JSON-RPC 请求 / 响应 / 流式片段。

### 限流

每个 token 默认 120 req/min（`rudder.mcp.rate-limit.per-minute` 可调）；超限返 HTTP 429 + `RATE_LIMITED` 错误码。

### 错误码

| HTTP | 含义 |
|------|------|
| 401 | Token 不存在 / 已撤销 / 已过期 |
| 403 | scope 或 RBAC 不允许 |
| 429 | 限流 |

> **提示**
> - Inspector 启动需要 Node.js ≥ 18。
> - 本地开发模式下，前端 dev server 在 5173 端口，MCP 协议入口在后端的 5680 端口。`{{baseUrl}}` 显示的是当前页面 origin，dev 模式请改用 `http://localhost:5680/mcp`；生产部署前后同源，直接用即可。
