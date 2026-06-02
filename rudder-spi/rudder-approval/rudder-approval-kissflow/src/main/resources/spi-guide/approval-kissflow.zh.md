---
description: Kissflow 工作流集成,企业审批平台
---

## Kissflow 审批接入指南

### 1. 创建 Service Account 与 Access Key

Kissflow API 使用 Access Key（ID + Secret 成对）鉴权，须在请求头中携带。外部系统对接应使用 **Service Account**（非自然人账号）的 Access Key，而非个人 Access Key：个人凭证在该用户被停用或删除后即失效，将中断对接。创建 Service Account 需 Super Admin 或 IAM Admin 权限。

1. 点击右上角头像，进入 **Account Administration → Service account**，填写名称（少于 80 字符）后创建
2. 在该 Service Account 的 **Access keys** 标签下点击 **Create access key**，填写名称并设置有效期（6 个月至 2 年）
3. 记录 **Access Key ID** 与 **Access Key Secret**，Secret 仅在创建时可见

### 2. 获取 Account ID

Kissflow 访问地址形如 `https://{account_id}.kissflow.com/`，地址中 `{account_id}` 即为 Account ID。

### 3. 创建审批流程

1. 进入 **Process → Create Process**
2. 设计表单字段（**字段 ID 须与下列约定完全一致**，系统直接按这些 ID 写入；字段 ID 在创建时设定、发布后不可更改、不能含空格，显示名可另取可读名称）：
   - 标题字段（单行文本），字段 ID `Title`
   - 内容字段（多行文本），字段 ID `Description`
   - 申请人字段（人员类型），字段 ID `Applicant`
   - 为每个会用到的阶段添加一个**人员（People）类型**字段，承接该阶段候选审批人，**字段 ID 等于阶段标识**：`PROJECT_OWNER` / `WORKSPACE_OWNER` / `SUPER_ADMIN`
3. 设置审批步骤：为每个阶段加一个审批节点，按 `PROJECT_OWNER → WORKSPACE_OWNER → SUPER_ADMIN` 顺序排列
   - 每个节点的审批人设为「动态指定 / 基于字段」，指向该阶段对应的人员字段
   - 为每个节点加条件：**对应人员字段为空时跳过该节点**
4. 发布流程
5. 在流程详情中复制 **Process ID**
6. 在 **Manage → 成员与权限** 中，将第 1 步创建的 Service Account 加为本流程成员并授予创建权限；Service Account 仅能访问被共享给它的流程，未授权时用其 Access Key 创建实例会被拒绝

> **工作原理**：系统按资源类型计算本次审批的阶段链与各阶段候选人，把候选人邮箱写入对应人员字段后创建实例；多级流转由 Kissflow 内部完成，系统仅创建实例并接收最终结果（通过/拒绝），不使用系统内置的逐级流转逻辑。
>
> 不同审批的阶段链长度不同（例如项目发布可能只需一级，MCP Token 申请需「空间负责人 → 平台管理员」两级），而流程节点是固定的。因此每个节点都要加「字段为空则跳过」条件：系统只填充本次需要的阶段字段，其余字段留空、对应节点自动跳过。按上述顺序排列三个节点即可覆盖当前全部审批场景。

### 4. 阶段字段命名约定

系统以**约定**对接，不在 Rudder 端配置任何字段映射：阶段标识直接作为人员字段的 ID 写入。阶段标识是一组固定值（闭集）：

- `PROJECT_OWNER` — 项目负责人
- `WORKSPACE_OWNER` — 工作空间负责人
- `SUPER_ADMIN` — 平台管理员（MCP Token、数据权限等高敏申请的二级审批）

只需为流程实际会触发的阶段创建同名人员字段（字段 ID 等于阶段标识）；用不到的阶段无需创建。某阶段缺少同名字段时，其候选人不会写入 Kissflow，对应节点将缺少审批人。

### 5. 审批发起人

系统会把当前操作用户的邮箱写入第 3 步约定的 `Applicant` 人员字段，供流程中需要时引用（例如按申请人上级路由：节点公式 `Applicant.Manager`）。

- 请确保系统中用户的邮箱与 Kissflow 账号邮箱一致
- 实例的创建者（`_created_by`）始终是 Access Key 对应的 Service Account，而非申请人；申请人身份仅通过 `Applicant` 字段传递

### 6. 配置审批结果回调

Kissflow 不提供固定格式的审批结果 Webhook，需在流程内通过 HTTP connector 将结果回传系统。

1. 进入 **Integrations → New integration**，触发器选择 **Kissflow Process** 并指向本流程，触发事件选择审批通过（流程完成）对应的事件
2. 添加 **HTTP → Make an HTTP call (POST)** 动作：
   - URL：`https://你的域名/api/approvals/callback/KISSFLOW`
   - Header：`Content-Type: application/json`
   - Body（raw / JSON），其中 `instanceId` 映射流程项的 Instance ID 变量、`approver` 映射审批人（可选）：

   ```json
   {"instanceId": "<Instance ID 变量>", "action": "APPROVED", "approver": "<审批人>"}
   ```
3. 再建一个由拒绝事件触发的集成，Body 中 `action` 改为固定值 `REJECTED`
4. 保存并激活集成

系统按 `instanceId` 反查审批记录，按 `action`（`APPROVED` / `REJECTED`）判定结果并结单。`instanceId` 须与创建实例时返回的 Instance ID 一致。请确保回调在流程到达终态时发出。

### 7. 填写配置

将上述信息填入下方对应字段，保存即可。保存后发布工作流时将自动在 Kissflow 中创建审批实例。
