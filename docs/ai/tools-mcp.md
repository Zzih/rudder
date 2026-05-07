# 工具 & MCP

> Agent 模式下 AI 能"动手干活",是因为挂了一堆工具。本文讲清楚工具怎么来、权限怎么管、怎么接外部 MCP。

## 工具分类

| 类别 | 来源 | 权限默认 |
|------|------|---------|
| **Native(内置)** | Java 代码,`AbstractBuiltinTool`(实现 `AgentTool` 契约) | 按前缀规则判定 |
| **MCP** | 外部 MCP server 注册进来 | 跟同名前缀规则 |
| **Skill-as-tool** | `SkillToolProvider` 把启用的 Skill 包装成工具 | 由 PermissionGate 走通用规则 |

## 内置工具清单

代码位于 `rudder-ai/src/main/java/io/github/zzih/rudder/service/ai/tool/builtin/`,共 **20 个**(不含 `AbstractBuiltinTool` 抽象类与 `MarkdownTables` 工具类):

### 读类(默认 VIEWER 可用,无需审批)

| 工具 name | 用途 |
|-----------|------|
| `list_datasources` | 列 workspace 可见数据源 |
| `list_catalogs` | 三层引擎列 catalog |
| `list_databases` | 列 database/schema |
| `list_tables` | 列表 |
| `list_folders` | 列脚本目录 |
| `list_scripts` | 列脚本 |
| `describe_table` | 表结构(列 / 类型 / 主键 / 注释) |
| `search_scripts` | 按关键字搜脚本 |
| `search_documents` | 检索知识库 |
| `sample_table` | 预览前 N 行(**经脱敏**) |
| `run_sql_readonly` | 执行 SELECT / SHOW / DESC / EXPLAIN / WITH(正则守卫只读) |
| `get_script_content` | 读脚本内容 |
| `get_execution_logs` | 读执行 log |
| `get_execution_result` | 读执行结果 |

### 写类(默认 DEVELOPER,**必须审批**)

| 工具 name | 用途 |
|-----------|------|
| `create_script` | 新建脚本 |
| `update_script` | 改脚本内容 |
| `rename_script` | 改脚本名 |
| `move_script` | 移动脚本 |
| `delete_script` | 删脚本 |
| `execute_script` | 触发执行 |

> 当前没有 `create_folder` 这类目录写工具;有需要走 IDE 自身 API。

## 权限模型

### 默认规则(代码内置,在 `PermissionGate` 中)

```
前缀 → (minRole, requireConfirm, readOnly)
  list_ / get_ / describe_ / search_ / run_sql_readonly / sample_  →  VIEWER, false, true
  create_ / update_ / rename_ / move_                              →  DEVELOPER, true, false
  delete_ / execute_                                               →  DEVELOPER, true, false
```

### 覆盖规则(Admin UI)

**Admin → AI 配置 → 工具权限**(`GET/POST/PUT/DELETE /api/ai/admin/tool-configs`,表 `t_r_ai_tool_config`)。每行字段:`toolName / workspaceIds / minRole / requireConfirm / readOnly / enabled`。

`workspaceIds`(JSON 字符串):
- `null` 或空 → 对**所有 workspace** 生效(平台级)
- 指定 id 列表 → 只对这些 workspace 生效

常见场景:

| 场景 | 配置 |
|------|------|
| 生产 workspace 强制 `execute_script` 审批 | toolName=`execute_script`, workspaceIds=[生产 wsId], requireConfirm=true |
| 内部工具对 VIEWER 开放 | toolName=`xxx_xx`, minRole=VIEWER |
| 灰度禁用某工具 | toolName=`delete_script`, workspaceIds=[wsId], enabled=false |

### 权限决策流

```
请求工具 xxx_yyy 的调用
  ↓
PermissionGate.check(toolName, ctx):
  1. 从 ToolConfigService 查 DB 配置(按 toolName + workspaceId)
  2. DB 无 → 走代码默认(按前缀)
  3. 判定:minRole? readOnly? enabled?
  4. 不符合 → throw ToolExecutionException
  ↓
(if requireConfirm)
  RudderToolCallback.waitForApproval:
  → ToolApprovalRegistry.register(streamId, toolCallId)
  → 前端 IDE 弹"Apply / Reject"卡片
  → 阻塞(5min 超时 = Reject)
  ↓
AgentTool.execute(input, ctx)
```

## 审批流

### 单节点路径

```
Agent 线程           前端           审批 HTTP 线程
   │                  │                  │
   await future ──→ 弹窗显示             │
                      │                  │
                      │ 用户点 Apply ─→  │
                      │                  │
                      │      POST /api/ai/streams/{streamId}/tool-approve
                      │                  ↓
                      │          future.complete(true) — 本地 JVM 直接唤醒
                      │                  │
   ←──── 返回 true  ──┤                  │
execute 工具                              │
```

同节点 ~μs,跨节点 ~10-50ms(pub/sub)。

### 跨节点

`ToolApprovalRegistry` 基于 `PubSubSignalRegistry`(本地 Caffeine 存 future + Redis pub/sub 广播),无论批准请求落在哪个节点都能唤醒。

### 超时 / 取消

- **5 分钟超时** → agent 收到 rejection,当拒绝处理,向 LLM 返回"User rejected the operation",LLM 可能换一个方案
- **turn 取消** → 每秒检查 `isCancelled()`,瞬时退出

## MCP(外部工具)

### 什么是 MCP

`Model Context Protocol` = 外部进程给 AI 提供工具的协议。Rudder 通过 `spring-ai-starter-mcp-client` 接入,`SpringAiMcpClient` / `SpringAiMcpClientManager` 负责生命周期管理(`rudder-ai/.../service/ai/mcp/`),工具适配走 `tool/McpToolAdapter`。

> 注:MCP 没有单独的 SPI 模块(不存在 `rudder-spi/rudder-mcp`),实现就在 AI 业务模块里直接复用 Spring AI MCP SDK。

### 两种 transport

表 `t_r_ai_mcp_server` 字段 `transport` ∈:

| transport | 适用 | 配置 |
|-----------|-----|------|
| **STDIO** | 本地子进程 | `command=npx -y @modelcontextprotocol/server-filesystem /data` |
| **HTTP_SSE** | 远程服务 | `url=https://mcp.example.com/sse` |

### 配置

**Admin → AI 配置 → MCP 服务器**(`GET/POST/PUT/DELETE /api/ai/mcp/servers`)。字段:`name / transport / command / url / env / credentials / toolAllowlist / enabled / healthStatus / lastHealthAt`。

`toolAllowlist`(JSON 字符串):
- 留空 → 暴露该 server 的所有工具
- 填 JSON 数组(如 `["list_repos", "search_code"]`) → 只暴露白名单工具

### 工具命名规则

MCP 工具在 Rudder 里通过 `McpToolAdapter` 注册。权限决策按工具名前缀走同一套规则。

### 健康检查

**Admin UI** 每条 MCP server 右侧显示 `healthStatus`:UP / DOWN / UNKNOWN。`POST /api/ai/mcp/refresh-health` 触发 ping。

DOWN 时该 server 的工具暂时从 `ToolRegistry.allForWorkspace()` 过滤掉,不注入 agent。

## 怎么加一个新工具(开发者)

### 内置工具

```java
@Component
public class MyTool extends AbstractBuiltinTool {
    @Override public String name() { return "get_dashboard_data"; }
    @Override public String description() { return "..."; }
    @Override public JsonNode inputSchema() { return SCHEMA; }
    @Override public String execute(JsonNode input, ToolExecutionContext ctx) {
        // 实际逻辑
    }
}
```

`ToolRegistry` 自动扫描所有 `AbstractBuiltinTool` Bean 并注册。部署即生效。

### 测试工具是否被 agent 发现

写一个 eval case:`mustCallTools: ["get_dashboard_data"]`,prompt 引导 AI 去用。通过表示工具已正确注册 + 进入 system prompt 的工具列表(见 [评测](eval.md))。

## 相关表

- `t_r_ai_tool_config` — 工具权限覆盖配置
- `t_r_ai_mcp_server` — MCP server 注册
- `t_r_ai_message` 里 `role=TOOL_CALL / TOOL_RESULT` — 工具调用轨迹

## 相关文档

- [开发者指南 - 审批交互](user-guide.md#工具与审批agent-模式)
- [评测](eval.md):工具调用断言
- [架构 - ToolApprovalRegistry](architecture.md)
