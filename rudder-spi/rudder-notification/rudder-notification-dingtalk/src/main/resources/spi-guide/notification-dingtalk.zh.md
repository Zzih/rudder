---
description: 钉钉自定义机器人 webhook
---

## 钉钉通知接入指南

### 1. 创建群机器人
1. 打开目标通知群,点击右上角「群设置 → 智能群助手 → 添加机器人」
2. 选择「自定义」(Webhook 接入)
3. 配置机器人头像与名称(建议包含 `Rudder` 字样以便识别)
4. 选择安全设置(详见下一节),保存
5. 复制生成的 webhook URL,形如:
   ```
   https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxxxxxxxxxxxxxxxx
   ```

### 2. 安全设置
钉钉自定义机器人**必须**启用至少一种安全策略,三种可任选一种或多种组合:

- **自定义关键词** — 消息正文须包含至少一个关键词。Rudder 默认消息包含 `Rudder` 字样,可直接配为关键词
- **加签** — 钉钉提供一个签名 secret,每次请求 Rudder 计算 `timestamp` + `sign` 附加到 URL。secret 须填到下方 `Secret` 字段
- **IP 段** — 仅允许指定 IP 调用,需将 Rudder Server 节点出口 IP 加入

加签模式的 secret 形如 `SECxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`,与 webhook URL 一同提供。

### 3. 填写配置
| 字段 | 必填 | 取值 |
|:---|:---|:---|
| Webhook URL | 是 | 机器人 webhook 完整 URL |
| Secret | 否 | 仅启用「加签」时填入;关键词 / IP 段模式留空 |

保存后立即生效。配置错误(如 secret 不匹配)时,钉钉接口返回 `sign not match` 错误,Rudder Server 端日志会记录该响应。

触发事件清单与运行时行为详见平台层文档 [notification.md](../../../../../docs/notification.md)。
