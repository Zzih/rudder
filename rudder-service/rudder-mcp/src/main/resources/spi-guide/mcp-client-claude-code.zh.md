---
description: Claude Code CLI（Anthropic 官方终端 dev agent）
---

### 添加

终端执行：

```bash
claude mcp add --transport http rudder {{baseUrl}} \
  --header "Authorization: Bearer rdr_pat_<your-token>"
```

### 验证

任意 Claude Code 会话内：

```
/mcp
```

`rudder` 应处于 `connected`，可在 prompt 里 `@rudder` 调用工具。

### 项目级共享

加 `--scope project` 写到项目 `.mcp.json`（可入 git）。**PAT 不要明文 commit**，用 env 引用：

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

每个用户 `export RUDDER_PAT=rdr_pat_xxx`。

### 移除

```bash
claude mcp remove rudder
```
