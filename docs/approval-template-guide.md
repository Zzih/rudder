# 外部审批渠道模板配置指南

> 适用于 Rudder 接入飞书审批 / Kissflow 等外部审批渠道时，**让模板支持多审批人 + 多阶段流转**。
>
> 设计原则：候选审批人列表（项目 owner / workspace owner）由 Rudder **运行时算出来**通过参数传给外部模板；
> 外部模板内部按阶段路由审批；最终一次回调 Rudder 终结。

---

## 1. 工作机制

```
                       Rudder                                 外部审批渠道
                       ──────                                 ──────────────
申请人触发审批                                                 飞书 / Kissflow
                                                                  │
   ApprovalService.submit                                          │
        │                                                         │
        ↓                                                         │
   StageFlow 算阶段链                                              │
   [PROJECT_OWNER, WORKSPACE_OWNER]                                │
        │                                                         │
        ↓                                                         │
   ApproverResolver 算各阶段候选人                                 │
   PROJECT_OWNER:    [alice@x.com]                                │
   WORKSPACE_OWNER:  [bob@x.com, charlie@x.com]                   │
        │                                                         │
        ↓                                                         │
   ApprovalNotifier.submitApproval                                │
        │ form 表单字段：                                          │
        │   title:        "工作流发布: daily_etl"                  │
        │   content:      "Publish workflow [...]"                │
        │   applicant:    bob@x.com (申请人)                       │
        │   widget_PROJECT_OWNER:    [alice]                      │
        │   widget_WORKSPACE_OWNER:  [bob, charlie]               │
        │                                                         │
        └──────────────────────────────────────────────────────→ 创建审批实例
                                                                  │
                                                  外部模板内部跑流转：
                                                  阶段 1 (alice 审批) → 通过
                                                  阶段 2 (bob/charlie 审批) → 通过
                                                                  │
                                                                  ↓
                                                            一次回调 Rudder
                                                                  │
                                                                  ↓
                          ApprovalService.resolveFromCallback (直接 finalize)
```

**关键点**：
- 外部渠道走的是"一次提交 → 内部多级流转 → 一次回调"
- Rudder 这边的多阶段 `advance` 路径**只有 LOCAL 渠道走**
- 外部渠道下，Rudder 的 `decision_rule / required_count` 字段不生效（决议规则下沉到外部模板）

---

## 2. 飞书审批模板配置

### 步骤 1：创建审批模板

进飞书管理后台 → 工作台 → 审批 → 自建审批 → 新建审批。

### 步骤 2：表单设计（添加控件）

需要的控件：

| 控件名（建议） | 飞书类型 | 说明 |
|---|---|---|
| 标题 | 单行文本 | Rudder 填审批标题 |
| 内容 | 多行文本 | Rudder 填审批描述 |
| 申请人 | **人员**（单选）| Rudder 填申请人 open_id（可选）|
| 一级审批人（项目级）| **人员**（多选）| Rudder 填项目 owner open_id 列表 |
| 二级审批人（工作空间级）| **人员**（多选）| Rudder 填 workspace owner open_id 列表 |

**记录每个控件的 widget id**——飞书后台界面会显示（类似 `widget_1741234567`），后面要填到 Rudder 配置。

### 步骤 3：审批节点设计

在飞书审批模板的"流程设计"页面：

```
┌──────────────────────────────────┐
│ 1. 审批节点：项目级审批          │
│    审批人 = ${一级审批人控件}    │ ← 引用人员控件
│    规则   = 任一通过 (Anyone)   │
│    （可选）条件：申请人 != 一级审批人时启用，否则跳过 │
└──────────────────────────────────┘
                 ↓
┌──────────────────────────────────┐
│ 2. 审批节点：工作空间级审批      │
│    审批人 = ${二级审批人控件}    │
│    规则   = 任一通过             │
└──────────────────────────────────┘
```

**飞书审批支持"动态人员"路由**：审批节点的"审批人"选择"动态指定"→ 选对应的人员控件。

### 步骤 4：Rudder Admin 配置

进 Rudder admin → 审批配置 → 新建/编辑飞书渠道：

```
渠道类型：       LARK
App ID：         cli_xxxxxxxxxxxxx
App Secret：     ********
Approval Code：  OA12345（飞书审批后台地址栏的 definitionCode）
标题控件 ID：    widget_aaaa
内容控件 ID：    widget_bbbb
申请人控件 ID：  widget_cccc       ← 可选
阶段→控件 ID JSON 映射：
{
  "PROJECT_OWNER":   "widget_dddd",
  "WORKSPACE_OWNER": "widget_eeee"
}
Encrypt Key：    ******** (可选)
Verification Token: ******** (可选)
```

**关键**：`stageFieldMapping` 的 key 必须跟 Rudder 的阶段名一致：
- `PROJECT_OWNER` —— 项目级审批阶段
- `WORKSPACE_OWNER` —— 工作空间级审批阶段
- 其他自定义阶段 —— 按 `ApprovalStageFlow` 实现里返回的字符串名

### 步骤 5：事件订阅（自动）

Rudder 首次提交审批时会自动调用飞书 `subscribe` API 激活事件推送。回调地址需要在飞书审批后台配置为：
```
https://your-rudder-host/api/approvals/callback
```

### 步骤 6：用户邮箱对应

候选人邮箱（Rudder 数据库 user.email）必须**等于该用户在飞书的注册邮箱**——Rudder 通过飞书 `batch_get_id_user` API 反查 open_id。如果对应不上：
- log 会有 warn `Lark batch resolved 0 open_ids from N emails`
- 该候选人不会被加到审批人列表
- 如果**所有**候选人都查不到，飞书会因审批人为空创建实例失败

---

## 3. Kissflow 模板配置

### 步骤 1：创建 Process

进 Kissflow → Processes → New Process → 起个名字（如 `Rudder Approval Workflow`）。

### 步骤 2：表单字段（Form Fields）

| Field Name（建议）| 类型 | 说明 |
|---|---|---|
| `Title` | Text | 审批标题 |
| `Description` | Long Text | 审批描述 |
| `Applicant` | User Picker (single) | 申请人（可选）|
| `Approver_Level_1` | User Picker (multi) | 项目级候选审批人 |
| `Approver_Level_2` | User Picker (multi) | 工作空间级候选审批人 |

Kissflow 的 Field Name 是用户自己起的可读字符串（不像飞书是自动生成的乱码），所以**配置非常直观**。

### 步骤 3：Workflow Steps

```
┌──────────────────────────────────┐
│ Step 1: Approval                 │
│   Approver = Dynamic → From Field│
│              → "Approver_Level_1"│
│   Decision = Anyone Can Approve  │
└──────────────────────────────────┘
                 ↓
┌──────────────────────────────────┐
│ Step 2: Approval                 │
│   Approver = Dynamic → From Field│
│              → "Approver_Level_2"│
│   Decision = Anyone Can Approve  │
└──────────────────────────────────┘
```

### 步骤 4：Rudder Admin 配置

```
渠道类型：     KISSFLOW
API Key：      ********
Account ID：   your-account
Process ID：   PUBLISH_APPROVAL
标题字段名：    Title              (默认 Title 可不填)
内容字段名：    Description        (默认 Description 可不填)
申请人字段名：  Applicant          (可选)
阶段→字段名 JSON 映射：
{
  "PROJECT_OWNER":   "Approver_Level_1",
  "WORKSPACE_OWNER": "Approver_Level_2"
}
```

### 步骤 5：Webhook 回调

进 Kissflow Process settings → Webhooks → 添加：
- URL：`https://your-rudder-host/api/approvals/callback`
- Events：Process Submission Status Changes

---

## 4. 多阶段对应"申请人是项目 owner 时跳过项目级"

Rudder 这边 `ProjectPublishStageFlow` / `WorkflowPublishStageFlow` 实现已经处理了这个逻辑：

```java
List<String> resolveStageChain(ApprovalRecord record) {
    boolean applicantIsProjectOwner = ...;
    return applicantIsProjectOwner
        ? List.of("WORKSPACE_OWNER")                       // 1 阶段
        : List.of("PROJECT_OWNER", "WORKSPACE_OWNER");     // 2 阶段
}
```

**对外部渠道的两种处理方式**：

### 方式 A：模板内部条件分支（推荐）
飞书审批支持"条件审批"：在审批节点上设置条件"`${申请人}` 不等于 `${一级审批人}` 时启用此节点"。
- Rudder 永远填两阶段候选人字段（即使阶段链只有一阶段，第二阶段填的就够用）
- 飞书自己根据条件决定是否跳过项目级
- 缺点：模板配置稍复杂

### 方式 B：Rudder 控制阶段填充
Rudder 已经按阶段链精确填充：
- 申请人是项目 owner 时：只填 `widget_WORKSPACE_OWNER`
- 申请人不是：填 `widget_PROJECT_OWNER` + `widget_WORKSPACE_OWNER`
- 飞书模板的项目级节点 Approval 配置成"如果 `${一级审批人}` 为空则跳过"
- 优点：模板逻辑更简单

**默认行为**：方式 B（Rudder 按阶段链填充对应字段）。

---

## 5. 故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 飞书报"审批人为空" | 候选人 email 在飞书查不到 open_id | 检查用户邮箱是否对应飞书账号；查 log `Lark batch resolved` |
| 提交审批后立刻回调 APPROVED 但单子没真审批 | 模板里没配审批节点（裸 form） | 检查飞书模板的"流程设计" |
| 阶段没配 widget mapping | log warn `stage 'X' has no widget mapping, skipping` | 检查 admin 后台的 `stageFieldMapping` JSON |
| stageFieldMapping JSON 解析失败 | JSON 格式错（缺引号 / 多余逗号） | log debug 后回退空 Map → 候选人不填 → 飞书报错 |
| 飞书审批通过后 Rudder 单子没终结 | 回调没收到 | 检查飞书事件订阅状态；检查 Rudder `/api/approvals/callback` 是否可达 |
| Kissflow Webhook 没触发 | Process 设置 → Webhook 没配 | 在 Kissflow 后台加 Webhook |
| Rudder 用户邮箱跟外部账户不一致 | user.email != 飞书/Kissflow 账户邮箱 | 一期不支持映射表，需保证一致 |

---

## 6. 多 resource_type 共用同一个模板

Rudder 一个飞书审批模板可被多种资源类型共用：
- `PROJECT_PUBLISH`、`WORKFLOW_PUBLISH`、`MCP_TOKEN`
- 它们的阶段链可能不同（如 MCP_TOKEN 只有 1 阶段）
- 同一份 `stageFieldMapping` 配置就够用（多余的阶段在该 resource_type 下不被填充）

唯一限制：模板里**不能强制要求所有阶段控件都必填**——单阶段场景下另一个控件会留空。把模板控件设为"非必填"。

---

## 7. 一期未实现

- **Rudder 用户邮箱 ↔ 飞书/Kissflow 账户**自动映射表（一期要求邮箱完全一致）
- **Kissflow user picker 通过 email 反查 user_id**（一期假定 Kissflow 接受 email 直接传入）
- 飞书人员控件填多个 open_id 的高级语法（一期用最简 `[ou_xxx, ou_yyy]` 列表）
- 多 widget 模板的批量配置编辑器（一期手动填 JSON）

---

## 相关文档

- [MCP](mcp.md) — `MCP_TOKEN` 资源类型的写权限审批走这条同一通道（每个 capability 独立审批单）
- [permissions.md](permissions.md) — 角色与候选审批人解析规则
