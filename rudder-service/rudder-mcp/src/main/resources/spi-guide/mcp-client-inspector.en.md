---
description: MCP Inspector — Anthropic's official debugger; browse tools, invoke them, watch live responses
---

`@modelcontextprotocol/inspector` is Anthropic's official MCP server debugger. It handles Streamable HTTP / SSE transport, protocol handshake, and streaming responses correctly — far better than raw `curl`, and the standard way to poke at an MCP server from the command line.

### Launch

```bash
npx @modelcontextprotocol/inspector
```

A browser window opens at `http://localhost:6274` (port may vary). On the left panel:

- **Transport Type**: `Streamable HTTP`
- **URL**: `{{baseUrl}}`
- **Authentication**: tick Bearer Token, paste your PAT (`rdr_pat_...`)

Click **Connect**. The `Tools` panel on the bottom-left lists all available tools. Pick one, fill arguments, hit `Run Tool` — the right panel shows the live JSON-RPC request, response, and streaming chunks.

### Rate limiting

Each token is capped at 120 req/min by default (tunable via `rudder.mcp.rate-limit.per-minute`). Over the limit returns HTTP 429 + error code `RATE_LIMITED`.

### Error codes

| HTTP | Meaning |
|------|---------|
| 401 | Token unknown / revoked / expired |
| 403 | Scope or RBAC denial |
| 429 | Rate limited |

> **Notes**
> - Inspector needs Node.js ≥ 18.
> - In local dev mode, the frontend dev server runs on port 5173 while the MCP protocol endpoint is on backend port 5680. The `{{baseUrl}}` shown reflects the current page origin — in dev, replace it with `http://localhost:5680/mcp`. In production they share the same origin, so just use it as-is.
