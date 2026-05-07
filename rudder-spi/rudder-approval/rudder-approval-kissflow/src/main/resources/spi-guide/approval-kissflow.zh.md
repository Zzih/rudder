---
description: Kissflow 工作流集成,企业审批平台
---

## Kissflow 审批接入指南

### 1. 获取 API Key
1. 登录 [Kissflow](https://kissflow.com/) 管理后台
2. 进入 **Settings → API & Integrations**
3. 生成 API Key 并记录

### 2. 获取 Account ID
1. 在浏览器地址栏中，Kissflow URL 格式为：`https://{account_id}.kissflow.com/`
2. 记录 `{account_id}` 部分

### 3. 创建审批流程
1. 进入 **Process → Create Process**
2. 设计表单：
   - 添加 `Title` 字段（文本类型）
   - 添加 `Description` 字段（多行文本类型）
3. 设置审批步骤：
   - 添加审批节点，配置审批人（固定或动态指定）
   - 多级审批由 Kissflow 流程控制，添加所需审批节点
4. 发布流程
5. 在流程详情中复制 **Process ID**

### 4. 配置 Webhook（接收审批结果回调）
1. 在流程设置中进入 **Webhooks**
2. 添加 Webhook URL：`https://你的域名/api/approvals/callback/KISSFLOW`
3. 订阅 `Instance Approved` 和 `Instance Rejected` 事件

### 5. 审批发起人
系统会在发布审批时自动获取当前操作用户的邮箱作为 Kissflow 流程发起人。
- 请确保系统中用户的邮箱与 Kissflow 账号邮箱一致
- 如果用户未配置邮箱，将使用 API Key 对应的默认账号发起

### 6. 填写配置
将上述信息填入下方对应字段，保存即可。保存后发布工作流时将自动在 Kissflow 中创建审批实例。
