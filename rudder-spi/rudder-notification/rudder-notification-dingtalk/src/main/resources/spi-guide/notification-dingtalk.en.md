---
description: DingTalk custom-bot webhook
---

## DingTalk notification setup

### 1. Create a group bot
1. Open the target DingTalk group, click the top-right menu → **Group Settings → Group Assistant → Add Bot**
2. Choose **Custom Bot** (Webhook integration)
3. Set the bot avatar and name (a name containing `Rudder` is recommended for traceability)
4. Pick at least one security strategy (see next section), then save
5. Copy the generated webhook URL:
   ```
   https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxxxxxxxxxxxxxxxx
   ```

### 2. Security strategy
DingTalk custom bots **require** at least one security strategy. Available options (combinable):

- **Custom keyword** — message body must contain at least one keyword. Rudder's default messages include `Rudder`; configure `Rudder` as the keyword
- **Signed payload** — DingTalk provides a signing secret; Rudder appends `timestamp` + `sign` to each request URL. The secret must be filled into the `Secret` field below
- **IP allowlist** — only requests from listed IPs are accepted; add the egress IPs of Rudder Server nodes

The signing secret looks like `SECxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`, supplied alongside the webhook URL.

### 3. Fill the form
| Field | Required | Value |
|:---|:---|:---|
| Webhook URL | Yes | Full bot webhook URL |
| Secret | No | Required only when **Signed payload** is enabled; leave empty for keyword / IP-allowlist modes |

Saving takes effect immediately. Misconfiguration such as a mismatched secret surfaces as `sign not match` in the DingTalk response and is recorded in the Rudder Server logs.

Triggered events and runtime behaviour are documented in the platform-level [notification.md](../../../../../docs/notification.md).
