---
description: Claude Code CLI (Anthropic's official terminal dev agent)
---

### Add

In your terminal:

```bash
claude mcp add --transport http rudder {{baseUrl}} \
  --header "Authorization: Bearer rdr_pat_<your-token>"
```

### Verify

Inside any Claude Code session:

```
/mcp
```

`rudder` should show `connected`. Use `@rudder` in a prompt to invoke tools.

### Share with a team

Append `--scope project` so the entry goes to the project's `.mcp.json` (committable). **Do not commit a plaintext PAT**; reference it via env instead:

```json
{
  "mcpServers": {
    "rudder": {
      "type": "http",
      "url": "{{baseUrl}}",
      "headers": { "Authorization": "Bearer ${RUDDER_PAT}" }
    }
  }
}
```

Each user runs `export RUDDER_PAT=rdr_pat_xxx`.

### Remove

```bash
claude mcp remove rudder
```
