---
description: Cursor IDE (VS Code-based AI editor)
---

Cursor has native MCP support since 0.42 and can call Rudder tools directly from the editor.

### Configuration file location

```
Per project:  <project-root>/.cursor/mcp.json
Global:       ~/.cursor/mcp.json
```

### Configuration content

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

### Steps

1. Create `.cursor/mcp.json` at your project root and paste the JSON above.
2. Replace `<your-token>` with your PAT.
3. Restart Cursor, or click Reload in `Cursor Settings → MCP`.
4. The `rudder` tool set should appear in the Composer / Chat panel.

> **Tip**: add `.cursor/` to `.gitignore` to avoid committing tokens.
