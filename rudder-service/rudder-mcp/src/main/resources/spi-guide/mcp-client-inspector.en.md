---
description: MCP Inspector — Anthropic's official debugger; browse tools, invoke them, watch live responses
---

### Prerequisites

Node.js 18+.

### Launch

```bash
npx @modelcontextprotocol/inspector
```

Opens `http://localhost:6274` in the browser. In the left panel:

- **Transport Type**: `Streamable HTTP`
- **URL**: `{{baseUrl}}`
- **Authentication**: tick Bearer Token, paste your PAT (`rdr_pat_...`)

Click **Connect**. The `Tools` panel lists all available tools. Pick one, fill arguments, hit `Run Tool` — the right panel shows the live request / response.

### Error codes

| HTTP | Meaning |
|:---|:---|
| 401 | Token unknown / revoked / expired |
| 403 | Scope or RBAC denial |
| 429 | Rate limited (default 120 req/min per token) |

> In local dev, `{{baseUrl}}` reflects the current page origin (port 5173); the MCP endpoint lives on backend port 5680, so use `http://localhost:5680/mcp` in dev. In production both share the same origin — use as-is.
