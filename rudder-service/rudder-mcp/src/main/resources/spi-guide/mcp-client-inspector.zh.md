---
description: MCP Inspector — Anthropic 官方调试器，浏览 tool / 触发调用 / 看实时响应
---

### 前置依赖

Node.js 18+。

### 启动

```bash
npx @modelcontextprotocol/inspector
```

自动开浏览器到 `http://localhost:6274`。左侧表单填：

- **Transport Type**：`Streamable HTTP`
- **URL**：`{{baseUrl}}`
- **Authentication**：勾 Bearer Token，填 PAT 明文（`rdr_pat_...`）

点 **Connect**，`Tools` 面板会列出所有工具。选一个填参数 → `Run Tool`，右侧实时看请求 / 响应。

### 错误码

| HTTP | 含义 |
|:---|:---|
| 401 | Token 不存在 / 已撤销 / 已过期 |
| 403 | scope 或 RBAC 不允许 |
| 429 | 限流（默认每个 token 120 req/min） |

> 本地开发模式下，`{{baseUrl}}` 取当前页面 origin（5173 端口）；MCP 协议入口在后端 5680 端口，dev 模式请改用 `http://localhost:5680/mcp`。生产部署前后同源，直接用即可。
