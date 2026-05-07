# 开发者指南

> 给日常在 IDE 里用 AI 的开发者。看完知道:怎么对话、怎么让 AI 干活、怎么取消、怎么让回答更准。

## 打开 AI

IDE 顶部/右侧找 **MagicStick** 图标 → 点开 AI 面板。面板右上角:

- **模式开关**(CHAT / AGENT) — 详见[下节](#chat-vs-agent)
- **Stop** 按钮(对话进行中显示) — 中断当前流

## CHAT vs AGENT

| | CHAT | AGENT |
|---|------|-------|
| 能调工具 | 否 | 可调所有 workspace 可见的工具 |
| 典型场景 | 问概念 / 解释 / 讨论 | "帮我查表结构再写 SQL" / "这任务跑挂了帮我看看" |
| 是否有审批 | 否 | 写类工具需批准 |
| 流式响应 | 是 | 是(工具调用夹在中间) |

**切换时机**:模式绑定在 session 上(`t_r_ai_session.mode`,执行器分派依据),一个 session 全程一个模式。要切,新建 session。UI 上切换开关只对**新 session** 生效。

## 自动上下文

**你不用手动贴任何东西**,发消息时 AI 自动收到:

| 段落 | 来自哪 |
|------|-------|
| **base-role** | 当前 workspaceId + 模式(CHAT/AGENT) |
| **dialect** | 当前脚本 TaskType(MYSQL / TRINO_SQL / ...) |
| **active-script** | 活跃 tab 脚本正文(截 4000 字) |
| **selection** | 编辑器当前选中 |
| **pinned-tables** | 你 ⭐ 过的表 + workspace 级 Pin |
| **datasource** | 活跃 tab 的数据源 |
| **RAG** | 若开 `injectWikiRag`,自动从知识库召回相关文档 |

各 section 开关由 `AiContextProfile` 决定(见 [Skill & Prompt - Context Profile](skills-prompts.md#context-profile))。

## 工具与审批(AGENT 模式)

### 读类工具(无需批准)

AI 直接调,你看到的是 tool_call 卡片 + 结果,无打扰:

- `list_datasources / list_databases / list_tables / list_catalogs / list_folders / list_scripts` — 枚举
- `describe_table` — 表结构(含列)
- `sample_table` — 前 N 行(**自动脱敏**)
- `get_script_content / search_scripts` — 脚本读
- `search_documents` — 查知识库
- `run_sql_readonly` — 执行 SELECT / SHOW 等(正则守卫,只读)
- `get_execution_logs / get_execution_result` — 执行产物

### 写类工具(必须批准)

AI 要执行 `create_script` / `update_script` / `delete_script` / `rename_script` / `move_script` / `execute_script` 时:

1. IDE 弹出卡片:**Apply / Reject**(后端等待 `POST /api/ai/streams/{streamId}/tool-approve`)
2. **5 分钟内不操作 = 自动 Reject**
3. Apply 后真落库;成功后自动刷文件树
4. 编辑器若有**未保存改动**,保护性跳过(保留本地 + 弹 warning)

审批拒绝后,AI 收到"User rejected the operation",会在下一轮尝试别的方案或直接告诉你。

## 取消 / 停止

AI 正在输出 token 或调工具时,点 **Stop** 按钮(`POST /api/ai/streams/{streamId}/cancel`):

1. 立即断 Flux 订阅(Spring AI stream 终止)
2. 设 cancelled flag,tool loop / approval wait 下次轮询退出
3. Interrupt 执行线程(阻塞调用兜底)

**取消不继续烧 token**。跨节点场景通过 Redis pub/sub 广播到持有 handle 的节点,延迟 10-50ms。

AGENT 模式正在执行工具时**不能瞬时中断**,会等当前工具完成后在下一轮入口退出。

## Pin 表

IDE 元数据面板 → 找到表 → 节点右边的 ⭐ 图标。

- **用户级 Pin**:只有你看得到,每次对话都注入这些表 schema
- **Workspace 级 Pin**(管理员专属):所有该 workspace 用户都自动注入

Pin 太多会占 token,建议核心 5-10 张。端点:`/api/ai/pinned-tables`。

## 列 PII 标签

元数据面板 → 表节点展开 → 列节点右侧 tag(只有 SUPER_ADMIN 能改):

- **HIGH** / **MEDIUM** / **LOW** / **KEEP**
- 影响 `sample_table` / `run_sql_readonly` 结果的脱敏程度
- 详见脱敏文档(平台级,代码在 `rudder-common` + `service.redaction.RedactionService`)

## IDE 右键 / 按钮入口

编辑器右键菜单或工具栏会提供 Explain / Optimize / Diagnose 等快捷动作。**这些按钮不再走独立的 oneshot 端点**:前端把模板化的 user message 发到 `POST /api/ai/sessions/{id}/turns`,走的还是普通 turn 链路(只是 prompt 由前端预填)。

## Apply 代码块

AI 回复中的 ` ```sql ` / ` ```python ` 代码块右上角:

- **Copy** — 复制到剪贴板
- **Apply** — 直接替换当前 tab 内容

Apply 时如果当前 tab 有**未保存改动**,保留本地 + 弹 warning,不覆盖。手工保存后再 Apply。

## 反馈

每条 AI 回答下方:👍 / 👎 — 存 `t_r_ai_feedback`(端点 `POST /api/ai/feedback`,查询 `GET /api/ai/feedback/messages/{messageId}`),管理员可筛错例分析。鼓励随手点。

## 常见使用场景

### "帮我查 bi.orders 表的最近 7 天订单"

AGENT 模式 + 对话:
```
我想看 bi.orders 表最近 7 天每天的订单数量
```

AI 会:
1. (如果 Pin 过 bi.orders)直接生成 SQL
2. 否则先 `describe_table bi.orders` 看字段名
3. 生成 `SELECT DATE(create_time) AS d, COUNT(*) FROM bi.orders WHERE create_time >= NOW() - INTERVAL 7 DAY GROUP BY DATE(create_time) ORDER BY d`
4. Apply 到编辑器 → 你执行看结果

### "这任务昨天跑挂了,帮我看看"

AGENT 模式:
```
昨天 12 点的 bi_daily_report 任务失败了,帮我看看原因
```

AI 自动:
1. `get_execution_logs` 拉昨天该任务的 log
2. 分析错误信息(OOM / 权限 / 依赖缺失 等)
3. 给出修复建议,可能直接 `update_script` 改代码(写类工具,**会让你批准**)

### "AI 回答的 SQL 字段名是错的"

原因可能是:
- 该表没 Pin,也没在知识库(没跑元数据同步)→ AI 只能靠幻觉猜字段
- 知识库里的 schema 是旧版本 → 手工触发元数据同步

修复:Pin 这张表,或让管理员跑一次元数据同步。

## 键盘快捷键

(当前 UI 暂无全局快捷键,后续可能补 Cmd+Enter 发送等)

## 相关文档

- [Skill & Prompt](skills-prompts.md):想定制 AI 行为
- [工具 & MCP](tools-mcp.md):了解工具分类和权限
- [故障排查](troubleshooting.md):遇到问题看这里
