# 审批

> 审批子系统为「需要他人批准才能生效」的操作提供统一的提交、流转、决议与回调机制。工作流 / 项目发布、MCP 写权限 token、数据权限申请都接入同一套审批引擎,审批渠道(本地 / 飞书 / Kissflow)由 SPI 决定具体落地。
>
> 本章覆盖整体架构、阶段与候选人模型、生命周期、业务集成与 SPI provider。各外部渠道的模板搭建步骤见 [外部审批渠道模板配置指南](approval-template-guide.md)。

## 架构

审批由三层组成:

```
业务模块                ApprovalIntegration         按 resourceType 接入审批,构建请求、消费终态事件
   │                    (publish / mcp / dataperm)
   ▼
审批引擎(server)       ApprovalService             生命周期编排:submit → decide / callback → finalize
   │                    ApprovalStageFlow           按 resourceType 解析阶段链
   │                    ApproverResolver            按阶段解析候选人
   ▼
渠道 SPI                ApprovalNotifier            向外部渠道提交、处理回调(LOCAL / LARK / KISSFLOW)
```

审批单落 `t_r_approval_record`,逐级决议落 `t_r_approval_decision`。渠道配置走通用 SPI 配置表,per type 最多一个启用 provider。

## 资源类型与阶段模型

审批的接入点用 `resourceType`(字符串常量 `ApprovalResourceType`)区分:

| resourceType | 业务 | 阶段链 |
|---|---|---|
| `PROJECT_PUBLISH` | 项目发布 | 无关联项目或申请人 = 项目 owner → `[WORKSPACE_OWNER]`;否则 `[PROJECT_OWNER, WORKSPACE_OWNER]` |
| `WORKFLOW_PUBLISH` | 工作流发布 | 同上(候选人解析逻辑不同) |
| `MCP_TOKEN` | MCP 写权限 token | 含高敏感能力(`workflow.publish` / `datasource.manage` / `execution.run`)→ `[WORKSPACE_OWNER, SUPER_ADMIN]`;否则 `[WORKSPACE_OWNER]` |
| `DATA_PERM_APPLY` | 数据权限申请 | `[WORKSPACE_OWNER, SUPER_ADMIN]` |

阶段标识为 `ApprovalLevel`:`PROJECT_OWNER` / `WORKSPACE_OWNER` / `SUPER_ADMIN`。

- **阶段链由 `ApprovalStageFlow` 解析**(`resolveStageChain(record)`),每个 resourceType 一个实现。发布类共享 `AbstractPublishStageFlow`。新增审批类型只需新建一个实现并声明 `resourceType()`,无需改动其他代码。
- **候选人由 `ApproverResolver` 运行时解析**(`ProjectOwnerResolver` / `WorkspaceOwnerResolver` / `SuperAdminResolver`),返回该阶段候选人 user_id 列表。运行时计算意味着审批期间 owner 变更立即生效;`WorkspaceOwnerResolver` / `SuperAdminResolver` 会排除申请人本人(`PROJECT_OWNER` 阶段因申请人是 owner 时已被 StageFlow 跳过,无需再排除)。

### 决议规则

`DecisionRule` 决定单阶段需多少候选人 `APPROVE` 才推进,与 `ApprovalRecord.requiredCount` 配合:

- `ANY_1` — 任一候选人通过即推进(`required_count = 1`)
- `N_OF_M` — 至少 N 个通过(`required_count = N`,调用方显式给定)
- `ALL` — 全部候选人通过(`required_count` = 运行时候选人总数)

## 生命周期

```
submit() ─► [PENDING] ─► decide()/resolveFromCallback() ─► advance() 或 finalize() ─► publishFinalized()
```

由 `ApprovalService` 编排:

- **submit()** — 解析阶段链与各阶段候选人,批查 user 拿 username + email,构建 `ApprovalRequest`(含按阶段分发的候选人邮箱),提交到当前启用渠道,入库主表。
- **decide()**(仅本地渠道) — 校验当前用户为当前阶段候选人 → 插决议记录 → 驳回则 `finalize(REJECTED)`;通过且满足 `requiredCount` 则推进下一阶段或 `finalize(APPROVED)`。
- **resolveFromCallback()**(外部渠道) — 按 `external_approval_id` 查单。Kissflow 带逐级明细(`stageDecisions`)时按阶段落库每级决议;外部模板内部已完成多级流转,Rudder 侧直接 `finalize`。
- **finalize()** — 乐观锁(`WHERE status = 'PENDING'`)更新终态,发送用户通知,发布 `ApprovalFinalizedEvent` 给下游。

`ApprovalStatus`:`PENDING` / `APPROVED` / `REJECTED` / `WITHDRAWN` / `EXPIRED`。

### 终态事件分发

终态事件经 `ApprovalIntegrationDispatcher` 分发:在提交事务内调用,内部移到事务提交后异步执行 `onFinalized`,失败按退避重试(最多三次,即首次 + 三次共四次执行),最终失败发告警。这保证审批单状态与下游业务变更解耦,审批事务不被长链路阻塞。`finalize` 用乐观锁(`WHERE status = 'PENDING'`)更新终态,并发多人决议时先到者赢得终结,后到者决议仍落库但不再触发状态迁移与下游事件。

### 启用开关与本地 fallback

平台级「启用审批」开关关闭时,所有走审批的操作在 `submit()` 处直接建 `APPROVED` 记录并发终态事件,跳过阶段解析与外部渠道。这一收口在 `ApprovalService.submit()` 单点完成,各接入方无需各自处理。

`ApprovalConfigService.enabled()` 区别于 `active()`:前者判断是否存在启用中的渠道配置,后者在无配置时回退到 `ApprovalPluginManager.FALLBACK_PROVIDER`(`LOCAL`)。审批关闭时的自动通过记录其 `channel` 同样补为 `LOCAL`,与启用路径口径一致,避免非空约束失败。

## 业务集成

接入审批有两种方式:

**一、需要终态副作用的** 实现 `ApprovalIntegration`(按 `resourceType` 注册),其 `onFinalized` 在审批进入终态后推进业务(必须幂等,可能被 dispatcher 重试)。当前两个实现:

- **MCP token**(`McpApprovalIntegration`)— `APPROVED` 时激活关联的 token scope 授权(`PENDING_APPROVAL → ACTIVE`),否则标记为 `REJECTED` 并失效 token 视图缓存。详见 [MCP](mcp.md)。
- **数据权限**(`DataPermApprovalIntegration`)— `APPROVED` 时反查申请上下文,幂等写授权表并立即触发 Reconciler。详见 [数据权限](data-permission.md)。

**二、查询时派生状态的** 直接调 `ApprovalService.submit()` 提交,不实现 `ApprovalIntegration`。**发布(publish)** 属此类:发布批次没有事件驱动的状态机,对外状态在查询时按关联审批单派生——`listByResource` 查出该批次的审批单,全部 `APPROVED` 视为通过(已执行则取 PUBLISHED / FAILED),按派生值做后端分页筛选。

## SPI provider

`ApprovalNotifier` 是渠道契约,由 `ApprovalPluginManager` 装配。提供三个 provider:

| provider | 说明 |
|---|---|
| `LOCAL` | 本地审批,无外部系统交互,流转完全由 `ApprovalService` 在 Rudder 内控制。同时是 fallback provider。 |
| `LARK`(飞书) | 经飞书审批实例 API 提交,候选人邮箱批量解析为 open_id,事件订阅回调(支持加密与签名校验)。 |
| `KISSFLOW` | 经 Kissflow Process v2 API 提交(Service Account 鉴权),人员字段按邮箱反查内部 user id,回调经 Progress API 逐级反查决议落库。 |

外部渠道的字段约定、模板搭建、回调配置见 [外部审批渠道模板配置指南](approval-template-guide.md)。新增 provider 实现 `ApprovalNotifierFactory` + `ApprovalNotifier`,放在 `rudder-spi/rudder-approval/rudder-approval-<provider>` 下,由 PluginManager 自动发现。

## REST 接口

`ApprovalController`(`/api/approvals`):

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/approvals` | 分页列表(可按状态过滤) |
| GET | `/api/approvals/{id}` | 详情(含各级决议) |
| GET | `/api/approvals/resource` | 按 `resourceType` + `resourceCode` 查关联审批 |
| POST | `/api/approvals/{id}/approve` | 批准(本地渠道,body 含 comment) |
| POST | `/api/approvals/{id}/reject` | 驳回(comment 必填) |
| POST | `/api/approvals/{id}/withdraw` | 撤回(仅申请人) |

`ApprovalCallbackController`:

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/approvals/callback/{channel}` | 外部渠道回调(`channel` = `LARK` / `KISSFLOW`) |

渠道配置(查询 / 切换 / 启用开关)归入 `ConfigController`(`/api/config/approval`)。

## 数据库表

```
t_r_approval_record       审批单:channel / external_approval_id / resource_type / resource_code
                          / workspace_id / project_code / status / stage_chain / current_stage
                          / decision_rule / required_count / ext_data(业务侧结构化数据) / 时间字段
t_r_approval_decision     逐级决议:approval_id / stage / decider_user_id / decision / decided_at / remark
                          (UNIQUE: approval_id + stage + decider_user_id,防同人同阶段重复)
```

`ext_data` 存业务在 `finalize` 时重建所需的结构化数据(如数据权限申请的 `DataPermApplyContext`)。`external_approval_id` 上有唯一索引供外部回调反查。

## 相关文档

- [外部审批渠道模板配置指南](approval-template-guide.md) — 飞书 / Kissflow 模板搭建
- [权限模型](permissions.md) — 阶段候选人对应的角色
- [数据权限](data-permission.md) — 数据权限申请的审批接入
- [MCP](mcp.md) — MCP 写权限 token 的审批接入
- [消息通知](notification.md) — 审批通知渠道
