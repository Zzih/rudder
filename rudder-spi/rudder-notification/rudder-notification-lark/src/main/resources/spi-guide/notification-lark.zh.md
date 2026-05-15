---
description: 飞书自定义机器人 webhook
---

## 飞书通知接入指南

### 1. 创建群机器人
1. 打开目标通知群,点击右上角「群设置 → 群机器人 → 添加机器人」
2. 选择「自定义机器人」,填写机器人名称(建议体现归属,如 `Rudder 通知`)
3. 复制生成的 webhook URL,形如:
   ```
   https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   ```

### 2. 安全策略(建议)
机器人添加页面提供三种安全策略,任选其一启用:

- **自定义关键词** — 消息正文须包含指定关键词。Rudder 默认消息已包含 `Rudder` 字样,直接将 `Rudder` 配为关键词即可
- **IP 白名单** — 仅允许指定 IP 调用。需将 Rudder Server 节点出口 IP 加入白名单
- **签名校验** — 飞书侧支持,Rudder 当前未实现该模式,如启用此项请同时保留关键词或 IP 白名单作为兜底

未启用任何安全策略时,持有 webhook URL 者即可向群内推送任意消息;须避免在公开仓库或截图中暴露该 URL。

### 3. 填写配置
| 字段 | 取值 |
|:---|:---|
| Webhook URL | 上一步复制的 webhook 完整 URL |

保存后立即生效。可在「管理 → 通知渠道」页面查看 sender 健康状态。

触发事件清单与运行时行为详见平台层文档 [notification.md](../../../../../docs/notification.md)。
