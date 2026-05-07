---
description: Claude Desktop 桌面客户端 (Anthropic 官方)
---

Anthropic 官方桌面应用，支持 MCP 协议直连。

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
      "url": "{{baseUrl}}",
      "headers": {
        "Authorization": "Bearer rdr_pat_<your-token>"
      }
    }
  }
}
```

### 步骤

1. 复制上面的配置 JSON,把 `<your-token>` 替换为你创建的 PAT 明文
2. 把内容写入对应平台的 `claude_desktop_config.json`
3. 完全退出并重启 Claude Desktop
4. 在对话框右下角点 🔌 图标确认 `rudder` 已连接

> **安全提示**:PAT 等同密码,只在创建那一刻可见。请用密码管理器存储,不要提交到 Git。
