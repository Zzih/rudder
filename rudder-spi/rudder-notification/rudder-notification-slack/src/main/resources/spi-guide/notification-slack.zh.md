---
description: Slack incoming webhook 消息推送
---

## Slack 通知接入指南

### 1. 创建 Incoming Webhook 应用
1. 打开 [Slack API 控制台](https://api.slack.com/apps),点击 **Create New App**
2. 选择 **From scratch**,填写 App 名称(如 `Rudder Notifier`)和目标 workspace
3. 进入应用左侧菜单 **Incoming Webhooks**,将 **Activate Incoming Webhooks** 切换为 **On**
4. 点击页面底部 **Add New Webhook to Workspace**,选择目标 channel,确认授权
5. 复制生成的 webhook URL,形如:
   ```
   https://hooks.slack.com/services/Txxxxxxx/Bxxxxxxx/xxxxxxxxxxxxxxxxxxxxxxxx
   ```

每个 webhook 绑定一个固定 channel;Rudder 同一时刻仅启用一个 active notification provider,故仅可绑定一个 channel。

### 2. 安全建议
Slack 的 Incoming Webhook URL 即凭证,**任何人持有该 URL 即可向绑定 channel 发消息**。须注意:

- 不要把 webhook URL 提交到公开仓库或 issue 截图
- 怀疑泄露时立即在 Slack App 控制台 **Revoke**,重新生成新 URL

Slack 不支持加签 / IP 白名单等服务端校验,泄露后吊销是唯一止损方式。

### 3. 填写配置
| 字段 | 取值 |
|:---|:---|
| Webhook URL | 上一步复制的 webhook 完整 URL |

保存后立即生效。

触发事件清单与运行时行为详见平台层文档 [notification.md](../../../../../docs/notification.md)。
