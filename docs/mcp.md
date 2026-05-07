# MCP（Model Context Protocol）

> 把 Rudder 平台能力暴露给外部 LLM 客户端。配置一个 token，Claude Desktop / Cursor / 自研 Agent 即可用统一协议浏览元数据、写脚本、跑工作流，所有调用走平台 RBAC + 审计。

## 是什么

Rudder 内置一个 MCP Server（基于 Spring AI MCP）。任意标准 MCP 客户端都可以把它当成"工具集"驱动：

- 申请 **Personal Access Token (PAT)** → 强绑单个工作空间 → 选要开放的能力
- 客户端在 `mcp.json` 里填 token + URL
- LLM 自动发现 **Tools / Resources / Completions**，按需调用
- 每次调用经过双重授权（token 范围 ∩ 当前角色），写入审计

读权限即勾即用；写权限提交审批，所有写权限合并成一份审批单（owner 一次决议覆盖整个 token）；SUPER_ADMIN 申请直接生效。

## 核心概念

| 概念 | 说明 |
|:---|:---|
| **Token (PAT)** | `rdr_pat_xxxxxx` 形式的不可恢复明文，bcrypt 落库；强绑**单个工作空间**，调用范围被锁死 |
| **Capability** | 能力 ID（如 `metadata.browse` / `execution.run`），共 19 个，按域分组 |
| **Tool** | LLM 主动调用的"操作"（共 35 个）。例：`script.create` / `execution.run_script` |
| **Resource** | LLM 按 URI 读的"资源"（共 15 个），URI 形如 `rudder://script/{code}` |
| **Completion** | URI 路径变量的前缀补全（共 3 个），让 LLM 不用先 list 就能猜到合法 code |
| **Grant** | Token × Capability 的授权记录；READ → 立即 ACTIVE，WRITE → 走审批 |
| **双闸门** | 每次调用同时验：token 含此 capability（scope 闸） + 当前角色允许此 capability（RBAC 闸） |

## 启用 MCP（管理员）

灰度开关，默认 **开启**。关闭时按需置环境变量：

```bash
export RUDDER_MCP_ENABLED=false
# 重启 rudder-api
```

启用后：
- `/mcp` → 外部 LLM 协议入口（PAT 鉴权，Streamable HTTP）
- `/api/mcp/*` → 前端管理 API（JWT 鉴权）
- 定时任务：每 30 分钟扫一次过期 token，落库标记为 EXPIRED

工作空间内的 **MCP** 顶级菜单自动显示。

## 创建 Token（用户）

进入 工作空间 → **MCP** → **My Tokens** → 「+ Create Token」。

### 三步对话框

1. **基础信息**
   - 名称：建议带客户端标识（如 `Claude Desktop on Mac`）
   - 描述：可选
   - 工作空间：从你的工作空间列表里**选一个**（一旦绑定不可改）
   - 过期时间：7 / 30 / 90 / 180 / 365 天（最长 1 年）

2. **勾选能力**
   - **只读能力**：默认全勾，提交即生效
   - **写操作能力**（仅 DEVELOPER+ 可见）：勾选后会触发审批；所有 WRITE 合并成一份单
   - SUPER_ADMIN 勾 WRITE 直接生效，无审批

3. **明文 token 展示**
   - 形式 `rdr_pat_xxxxxxxxxxx...`
   - **只展示一次**，关掉就看不到，必须立即复制保存
   - 强制勾"我已保存"才能关闭

撤销：在列表点 「撤销」，确认后立即失效，所有缓存清空。撤销不可逆。

## 19 个 Capability

按域分组，4 角色矩阵：

| Capability | 域 | RW | 敏感度 | VIEWER | DEVELOPER | WORKSPACE_OWNER | SUPER_ADMIN |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| `workspace.view` | workspace | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `metadata.browse` | metadata | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `datasource.view` | datasource | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `datasource.test` | datasource | R | NORMAL | | ✓ | ✓ | ✓ |
| `datasource.manage` | datasource | W | **HIGH** | | | | ✓ |
| `project.browse` | project | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `project.author` | project | W | NORMAL | | ✓ | ✓ | ✓ |
| `script.browse` | script | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `script.author` | script | W | NORMAL | | ✓ | ✓ | ✓ |
| `execution.view_status` | execution | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `execution.view_result` | execution | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `execution.run` | execution | W | **HIGH** | | ✓ | ✓ | ✓ |
| `execution.cancel` | execution | W | NORMAL | | ✓ | ✓ | ✓ |
| `workflow.browse` | workflow | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `workflow.author` | workflow | W | NORMAL | | ✓ | ✓ | ✓ |
| `workflow.run` | workflow | W | NORMAL | | ✓ | ✓ | ✓ |
| `workflow.publish` | workflow | W | **HIGH** | | | ✓ | ✓ |
| `approval.view` | approval | R | NORMAL | ✓ | ✓ | ✓ | ✓ |
| `approval.act` | approval | W | NORMAL | | | ✓ | ✓ |

> 角色是**运行时**生效的：你的角色被工作空间 owner 改了，token 不需要重新申请——下一次调用立即按新角色判定，超出新角色的 grant 自动撤销。

## 35 个 Tool（按域）

### workspace（无 tool，资源化暴露）

只通过 Resource 访问，见下文 §Resources。

### metadata

| Tool | Capability | 说明 |
|:---|:---|:---|
| `metadata.search` | `metadata.browse` | 全局元数据搜索（datasource + keyword） |

### datasource

| Tool | Capability | 说明 |
|:---|:---|:---|
| `datasource.list` | `datasource.view` | 列出 workspace 数据源（凭证脱敏） |
| `datasource.test_connection` | `datasource.test` | 测连通性 |
| `datasource.create` | `datasource.manage` | 创建数据源（仅 SUPER_ADMIN） |
| `datasource.update` | `datasource.manage` | 更新数据源（仅 SUPER_ADMIN） |
| `datasource.delete` | `datasource.manage` | 删除数据源（仅 SUPER_ADMIN） |

### project

| Tool | Capability | 说明 |
|:---|:---|:---|
| `project.list` | `project.browse` | 列出工作空间下的项目 |
| `project.create` | `project.author` | 创建项目 |
| `project.update` | `project.author` | 更新项目 |
| `project.delete` | `project.author` | 删除项目（仍含工作流时拒绝） |

### script

| Tool | Capability | 说明 |
|:---|:---|:---|
| `script.list` | `script.browse` | 列工作空间脚本 |
| `script.create` | `script.author` | 创建脚本 |
| `script.update` | `script.author` | 更新脚本（增量） |
| `script.delete` | `script.author` | 删除脚本 |
| `script_dir.list` | `script.browse` | 列脚本目录树 |
| `script_dir.create` | `script.author` | 新建脚本目录 |
| `script_dir.rename` | `script.author` | 重命名脚本目录 |
| `script_dir.move` | `script.author` | 移动脚本目录 |
| `script_dir.delete` | `script.author` | 删除脚本目录（含子节点） |

### execution

| Tool | Capability | 说明 |
|:---|:---|:---|
| `execution.log` | `execution.view_status` | 任务日志（按 offsetLine 分页） |
| `execution.result` | `execution.view_result` | 任务结果（columns + 分页 rows，最大 1000/页） |
| `execution.run_direct` | `execution.run` | 即兴 SQL/脚本（不绑 script，异步） |
| `execution.run_script` | `execution.run` | 运行已存在的脚本（可覆盖 SQL/数据源/参数，异步） |
| `execution.cancel` | `execution.cancel` | 取消运行中任务 |

### workflow

| Tool | Capability | 说明 |
|:---|:---|:---|
| `workflow.list` | `workflow.browse` | 列工作流定义（不含 DAG） |
| `workflow.create` | `workflow.author` | 创建工作流 |
| `workflow.delete` | `workflow.author` | 删除工作流 |
| `workflow.run` | `workflow.run` | 触发工作流（MANUAL，异步） |
| `workflow.publish` | `workflow.publish` | 提交发布审批；SUPER_ADMIN 直接发布 |
| `workflow_instance.list` | `workflow.browse` | 列 workspace 内最近的运行实例 |
| `workflow_instance.page_by_workflow` | `workflow.browse` | 按工作流定义分页查实例 |
| `workflow_instance.cancel` | `workflow.run` | 取消运行中工作流实例 |
| `workflow_schedule.set` | `workflow.author` | 设置/更新 cron 调度 |

### approval

| Tool | Capability | 说明 |
|:---|:---|:---|
| `approval.list` | `approval.view` | 列出审批单（按状态过滤） |
| `approval.decide` | `approval.act` | 通过 / 拒绝（仅当前阶段候选人） |

## 15 个 Resource（按 URI）

`@McpResource` 让 LLM 通过稳定 URI 直接读取数据，区别于 Tool 的"操作"语义。返回类型 `application/json`。

| URI | Capability | 说明 |
|:---|:---|:---|
| `rudder://workspace/current` | `workspace.view` | 当前 token 绑的 workspace（id/name/description） |
| `rudder://workspace/members` | `workspace.view` | workspace 成员 + 角色 |
| `rudder://project/{code}` | `project.browse` | 单个项目 |
| `rudder://script/{code}` | `script.browse` | 单个脚本（含 content） |
| `rudder://workflow/{projectCode}/{code}` | `workflow.browse` | 工作流定义（含 DAG） |
| `rudder://workflow_instance/{workflowCode}/{instanceId}` | `workflow.browse` | 工作流运行实例（status/version/dag snapshot） |
| `rudder://workflow_instance/{instanceId}/nodes` | `workflow.browse` | 实例的节点级 task-instance 列表 |
| `rudder://workflow_schedule/{workflowCode}` | `workflow.browse` | 工作流的 cron 调度配置 |
| `rudder://datasource/{name}` | `datasource.view` | 单个数据源（凭证脱敏） |
| `rudder://datasource/{name}/catalogs` | `datasource.view` | 数据源 catalog 列表（仅 STARROCKS / TRINO） |
| `rudder://datasource/{name}/catalog/{catalog}/databases` | `datasource.view` | catalog 下的 database/schema |
| `rudder://datasource/{ds}/catalog/{cat}/database/{db}/tables` | `metadata.browse` | 表列表 |
| `rudder://datasource/{ds}/catalog/{cat}/database/{db}/table/{tb}` | `metadata.browse` | 表详情（columns + comment） |
| `rudder://datasource/{ds}/catalog/{cat}/database/{db}/table/{tb}/columns` | `metadata.browse` | 表的列 |
| `rudder://execution/{id}` | `execution.view_status` | 任务实例状态/host/timestamps |

> 单 catalog 引擎（MySQL / Hive）的 `{catalog}` 段填 `-`。

## 3 个 Completion（路径变量补全）

让 LLM 在不调 `*.list` 工具的前提下，根据前缀直接拿到合法 URI 变量值：

| URI Pattern | 变量 | 行为 |
|:---|:---|:---|
| `rudder://script/{code}` | `code` | 当前 workspace 下匹配前缀的 script code，最多 50 |
| `rudder://project/{code}` | `code` | 当前 workspace 下匹配前缀的 project code，最多 50 |
| `rudder://datasource/{name}` | `name` | 当前 workspace 可见数据源名，最多 50 |

## 接入客户端

进入 工作空间 → **MCP** → **Connect Guide** 拿到现成的配置模板。也可手填：

### Claude Desktop

`~/Library/Application Support/Claude/claude_desktop_config.json`（macOS）

```json
{
  "mcpServers": {
    "rudder": {
      "url": "https://rudder.example.com/mcp",
      "headers": {
        "Authorization": "Bearer rdr_pat_xxxxxxxx"
      }
    }
  }
}
```

重启 Claude Desktop。在新会话里 LLM 应能列出 Rudder 的 tools + resources。

### Cursor

`.cursor/mcp.json`（项目根 / 用户配置目录）

```json
{
  "mcpServers": {
    "rudder": {
      "url": "https://rudder.example.com/mcp",
      "headers": {
        "Authorization": "Bearer rdr_pat_xxxxxxxx"
      }
    }
  }
}
```

### MCP Inspector（调试）

```bash
npx @modelcontextprotocol/inspector
# 浏览器打开后填 URL = https://rudder.example.com/mcp，Authorization Bearer rdr_pat_xxx
```

可视化所有 tools / resources / completions，逐个调用方便排查权限和 schema。

### 自研 Agent / SDK

协议层由 [Spring AI MCP server](https://docs.spring.io/spring-ai/reference/2.0/api/mcp/mcp-overview.html) 提供：MCP JSON-RPC 2.0 over Streamable HTTP，按 `Accept` 头自动选择 JSON / SSE。protocol 模式由 `spring.ai.mcp.server.protocol`（默认 `STREAMABLE`）控制。

```bash
# tools/list
curl -X POST https://rudder.example.com/mcp \
  -H "Authorization: Bearer rdr_pat_xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# tools/call
curl -X POST https://rudder.example.com/mcp \
  -H "Authorization: Bearer rdr_pat_xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call",
       "params":{"name":"metadata.search",
                 "arguments":{"datasourceName":"warehouse","keyword":"user_orders"}}}'

# resources/read
curl -X POST https://rudder.example.com/mcp \
  -H "Authorization: Bearer rdr_pat_xxx" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"resources/read",
       "params":{"uri":"rudder://workspace/current"}}'
```

错误码遵循 JSON-RPC：`-32600` Invalid Request / `-32601` Method not found / `-32602` Invalid params / `-32603` Internal error。

## 写权限审批

勾选任何 WRITE capability 创建 token 时，**整个 token 的所有 WRITE capability 合并成一份审批单**，owner 一次决议（全过/全拒）。审批链按 capability 列表里 **最高敏感度** 决定：

| 单内最高敏感度 | 阶段链 |
|:---|:---|
| **NORMAL**（如 `script.author` / `workflow.author` / `workflow.run` / `execution.cancel` / `project.author` / `approval.act`） | `[WORKSPACE_OWNER]`，任一 owner 通过即生效 |
| **HIGH**（含 `workflow.publish` / `datasource.manage` / `execution.run` 之一） | `[WORKSPACE_OWNER, SUPER_ADMIN]`，每阶段 ANY-1 |

```
Token 入库 (status=ACTIVE)
   ├─ READ grant         → 立即 ACTIVE，可用
   └─ WRITE grants ─────→ 一份审批单（共享 approvalId）
                          含 HIGH? 是 → [WORKSPACE_OWNER, SUPER_ADMIN]
                          否       → [WORKSPACE_OWNER]
   决议通过 → 全部 WRITE grant → ACTIVE
   任一阶段拒绝 → 全部 WRITE grant → REJECTED
```

**SUPER_ADMIN 跳审批**：申请人是 SUPER_ADMIN 时所有 WRITE grant 直接 ACTIVE，不发审批单（同样适用于 `workflow.publish` 工具调用 → 直接发布到生产，跳过 PublishRecord 的 PENDING_APPROVAL 阶段）。

申请人状态可视化：在 「My Tokens」 → 「Detail」 看每个 grant 的状态：`ACTIVE` / `PENDING_APPROVAL` / `REJECTED` / `REVOKED`。

> 审批渠道（站内 / 飞书 / Kissflow）由平台管理员在 **管理 → 审批配置** 里配。配置和发布审批共用一套：见 [approval-template-guide.md](approval-template-guide.md)。

## 自动失效场景

下列情况无需人工干预，token / grant 自动失效：

| 场景 | 行为 |
|:---|:---|
| 用户被移出工作空间 | 该 user 在该 workspace 全部 ACTIVE token 立即整体撤销 |
| 用户角色被降级 | 超出新角色的 grant 单独撤销，其余保留 |
| Token 过期 | 认证路径实时拒绝；定时任务把 status 同步为 EXPIRED |
| 用户主动撤销 | UI 列表「撤销」按钮，确认即失效 |
| Token bcrypt 不匹配 | 验证失败立即 401；正常验证 5s 缓存避热路径 |

## 审计

每次 tool / resource 调用 → `t_r_audit_log` 一条：

| 字段 | 取值 |
|:---|:---|
| `module` | `MCP` |
| `action` | `TOOL_CALL` |
| `resource_type` | `MCP_TOKEN` |
| `resource_code` | tokenId |
| `status` | `OK` / `DENIED` / `FAILURE` |
| `client_ip` | 客户端 IP（PatAuthFilter 解析 X-Forwarded-For） |
| `request_method` / `request_uri` | HTTP 元信息 |
| `request_params` | 完整 input JSON（截断 4KB） |
| `description` | `tool=metadata.search, capability=metadata.browse[, code=DENIED_RBAC]`；resource 时格式 `resource=rudder://...` |
| `duration_ms` | tool 执行耗时 |

进 **管理 → 审计日志** 按 `module=MCP` 过滤即可查到全部 MCP 调用历史。

## 限流

每 token 默认 **120 次/分钟**（固定窗口算法）。可调：

```yaml
rudder:
  mcp:
    rate-limit:
      per-minute: ${RUDDER_MCP_RATE_LIMIT_PER_MINUTE:120}    # 0 = 不限流
```

超限返回 HTTP 429 + JSON `{ "code": 429, "message": "MCP token rate limit exceeded (120 req/min)" }`，并写审计 `status=DENIED, code=RATE_LIMITED`。

## Streamable HTTP transport

`POST /mcp` 由 Spring AI MCP server 提供，自动按 client 的 `Accept` 头路由 `application/json`（同步）/ `text/event-stream`（SSE）。本平台使用 `SYNC` 模式（便于 ThreadLocal 透传 UserContext）。

35 个 `@McpTool` / 15 个 `@McpResource` / 3 个 `@McpComplete` 在启动时由 Spring AI 自动扫描注入到 server，protocol 层、SSE、tool inputSchema 自动生成都由框架完成。`McpToolGuardAspect` AOP 切面统一拦截 `(@McpTool || @McpResource) && @McpCapability`，做限流 / 双闸门 / 审计三件事。

## 多实例部署

跨节点失效广播：撤销 / 角色降级 / 审批通过时，本节点清 `TokenViewCache` + 发 Redis pub `mcp:token:invalidate`，其他节点订阅后清各自副本。

```
Node A 撤销 token 99
   ├ 本地 cache.invalidateByTokenId(99)
   ├ Redis PUBLISH mcp:token:invalidate "99"
   ▼
Node B / C 订阅者 onMessage("99") → cache.invalidateByTokenIdLocal(99)
```

Redis 不可达时退化为单机模式，5s TTL 兜底。

## 安全建议

- **不要把 token 写进 git / 共享文件夹**。git pre-commit hook 已扫描 `rdr_pat_` 前缀
- **过期时间设短**：日常用 30 天，CI 长任务用 180 天，不要默认勾 365 天
- **写权限按需勾**：READ 能覆盖 80% 探索类场景，写权限申请前评估 LLM 误操作风险
- **泄漏处理**：立即撤销，撤销后 5 秒内全平台失效（bcrypt 缓存 TTL 上限）
- **多客户端用多 token**：每个客户端一个独立 token，泄漏时只撤销受影响的那个

## 排障

| 现象 | 排查方向 |
|:---|:---|
| 客户端连不上，401 | 检查 token 是否撤销 / 过期 / 拼错 |
| 客户端连上了但看不到任何 tool | `RUDDER_MCP_ENABLED=false`；服务端日志找 "MCP enabled" |
| 调 tool 报 403 `does not have capability` | token 没勾这个能力，需新建 token 时勾上（或申请 WRITE 审批） |
| 调 tool 报 403 `Current role not allowed` | 你的工作空间角色被降级，超出范围；让 owner 调回或新建 token |
| WRITE 审批一直 pending | 进 **审批 → 我的待审批** 看 owner 列表是否齐全；外部审批渠道（飞书 / Kissflow）则到对应平台找 |
| Resource 读 `Method must return ...` | Spring AI 框架要求 resource 返回 `String` / `List<String>` / `ResourceContents` 等，DTO 会被拒；本仓库已统一 JSON 序列化 |
| Claude Desktop 启动后无 tool 列表 | Claude Desktop 日志（`~/Library/Logs/Claude/`）看 MCP 协议握手是否成功 |
| 调用很慢（首次 80~100ms） | 正常 — bcrypt 验证耗时；同 token 5 秒内连续调用走缓存 |

## 演进路线

下面已经评估过当前不做：

| 范畴 | 当前 | 何时做 |
|:---|:---|:---|
| OAuth 2.1 + DCR | 仅 PAT | 主流 MCP 客户端开始要求时；目前都用 PAT 配 `mcp.json` |
| Tool 中间进度推送 | 单次 SSE event 后关闭 | 出现长耗时 tool（>30s）需要前端进度条时；当前都是秒级 |
| Resource 订阅（subscribe） | 不支持 | 客户端需要响应资源变更（如 workflow 状态推送）时 |
| 写权限逐项决议 | 整 token 一份审批，全过/全拒 | 出现"owner 想批一部分拒一部分"的真实需求时 |

## 相关文档

- [approval-template-guide.md](approval-template-guide.md) — 飞书 / Kissflow 模板配置
- [permissions.md](permissions.md) — 角色 / 工作空间 / 数据源三层权限模型
- [datasource.md](datasource.md) — 数据源凭证加密与授权
