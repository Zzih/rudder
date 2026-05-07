---
description: Claude Desktop (Anthropic's official desktop client)
---

Anthropic's official desktop application with native MCP support.

### Configuration file location

```
macOS:    ~/Library/Application Support/Claude/claude_desktop_config.json
Windows:  %APPDATA%\Claude\claude_desktop_config.json
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

1. Copy the JSON above, replace `<your-token>` with your PAT plaintext.
2. Save it as `claude_desktop_config.json` at the path for your OS.
3. Quit and restart Claude Desktop completely.
4. Click the 🔌 icon at the bottom-right of the chat box; you should see `rudder` listed.

> **Security**: a PAT is as sensitive as a password and is only shown once at creation time. Store it in a password manager — do not commit it to Git.
