---
description: Claude Desktop 桌面客户端（Anthropic 官方）
---

### 前置依赖

本机安装 Node.js 18+。

### 配置文件路径

```
macOS:    ~/Library/Application Support/Claude/claude_desktop_config.json
Windows:  %APPDATA%\Claude\claude_desktop_config.json
```

### 配置内容

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

### 步骤

1. 复制上面的 JSON，把 `rdr_pat_<your-token>` 替换为你的 PAT。
2. 写入对应平台的 `claude_desktop_config.json`（不存在则新建）。
3. 完全退出并重启 Claude Desktop。
4. 在对话框右下角点 🔌 图标，确认 `rudder` 已列出。

### 排障

| 现象 | 处理 |
|:---|:---|
| 没出现 rudder 工具集 | 查日志 `~/Library/Logs/Claude/mcp-server-rudder.log` |
| 401 | PAT 已撤销 / 过期，到「My Tokens」重建 |

> PAT 等同密码，只在创建那一刻可见。请用密码管理器存储，不要提交到 Git。
