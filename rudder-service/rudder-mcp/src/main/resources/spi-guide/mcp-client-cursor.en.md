---
description: Cursor IDE (VS Code-based AI editor)
---

### Option 1: add via Settings

`Cursor Settings → MCP & Integrations → Add Custom MCP`:

- **Name**: `rudder`
- **Type**: `http`
- **URL**: `{{baseUrl}}`
- **Headers**: `Authorization: Bearer rdr_pat_<your-token>`

Save and it takes effect. The `rudder` tool set appears in the Composer / Chat panel.

### Option 2: edit the config file

Path:

```
Per project:  <project-root>/.cursor/mcp.json
Global:       ~/.cursor/mcp.json
```

Content:

```json
{
  "mcpServers": {
    "rudder": {
      "url": "{{baseUrl}}",
      "headers": {
        "Authorization": "Bearer rdr_pat_<your-token>"
      }
    }
  }
}
```

Steps:

1. Create `.cursor/mcp.json` at the project root and paste the JSON above.
2. Replace `<your-token>` with your PAT.
3. Restart Cursor, or click Reload in `Cursor Settings → MCP`.

> Gitignore the project-level `.cursor/mcp.json` or reference the PAT via `${RUDDER_PAT}` to avoid committing the secret.
