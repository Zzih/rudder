---
description: Claude Desktop (Anthropic's official desktop client)
---

### Prerequisites

Node.js 18+ on the machine.

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
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "{{baseUrl}}",
        "--header",
        "Authorization:${AUTH_HEADER}"
      ],
      "env": {
        "AUTH_HEADER": "Bearer rdr_pat_<your-token>"
      }
    }
  }
}
```

### Steps

1. Copy the JSON, replace `rdr_pat_<your-token>` with your PAT.
2. Write to `claude_desktop_config.json` at the OS-specific path (create if missing).
3. Quit Claude Desktop completely and relaunch.
4. Click the 🔌 icon at the bottom-right of the chat box — `rudder` should appear.

### Troubleshooting

| Symptom | Action |
|:---|:---|
| `rudder` not listed | Check `~/Library/Logs/Claude/mcp-server-rudder.log` |
| 401 | PAT revoked / expired — rebuild from "My Tokens" |

> A PAT is as sensitive as a password, shown only once at creation time. Store it in a password manager — never commit it to Git.
