---
description: Cursor IDE (基于 VS Code 的 AI 编辑器)
---

### 方式 1：Settings 添加

`Cursor Settings → MCP & Integrations → Add Custom MCP`：

- **Name**: `rudder`
- **Type**: `http`
- **URL**: `{{baseUrl}}`
- **Headers**: `Authorization: Bearer rdr_pat_<your-token>`

保存即生效。Composer / Chat 面板会出现 `rudder` 工具集。

### 方式 2：编辑配置文件

配置文件路径：

```
项目级:  <project-root>/.cursor/mcp.json
全局:    ~/.cursor/mcp.json
```

配置内容：

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

步骤：

1. 在项目根目录创建 `.cursor/mcp.json`，粘贴上面 JSON
2. 用你的 PAT 替换 `<your-token>`
3. 重启 Cursor 或在 `Cursor Settings → MCP` 里点 Reload

> 项目级 `.cursor/mcp.json` 加入 `.gitignore` 或改用 `${RUDDER_PAT}` 引用 env，避免 token 误提交。
