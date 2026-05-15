---
description: Lark / Feishu custom-bot webhook
---

## Lark notification setup

### 1. Create a group bot
1. Open the target Lark group, click the top-right menu → **Group Settings → Bots → Add Bot**
2. Choose **Custom Bot**, give it a descriptive name (e.g. `Rudder Notifier`)
3. Copy the generated webhook URL, which looks like:
   ```
   https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   ```

### 2. Security strategy (recommended)
The bot creation dialog offers three security strategies; pick one:

- **Custom keyword** — messages must contain the configured keyword. Rudder's default messages include the literal `Rudder`; configuring `Rudder` as the keyword is sufficient
- **IP allowlist** — only requests from listed IPs are accepted. Add the egress IPs of Rudder Server nodes
- **Signed payload** — supported by Lark but not yet implemented in Rudder; if enabled, retain keyword or IP allowlist as the effective check

Without any strategy, anyone with the webhook URL can post arbitrary messages. Treat the URL as a secret.

### 3. Fill the form
| Field | Value |
|:---|:---|
| Webhook URL | The full webhook URL copied above |

Saving the configuration takes effect immediately. The sender health status is shown on the **Admin → Notification Channels** page.

Triggered events and runtime behaviour are documented in the platform-level [notification.md](../../../../../docs/notification.md).
