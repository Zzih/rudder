---
description: Slack incoming webhook delivery
---

## Slack notification setup

### 1. Create an Incoming Webhook app
1. Open the [Slack API console](https://api.slack.com/apps) and click **Create New App**
2. Choose **From scratch**, give the app a name (e.g. `Rudder Notifier`) and pick the target workspace
3. In the left sidebar select **Incoming Webhooks**, then toggle **Activate Incoming Webhooks** to **On**
4. Click **Add New Webhook to Workspace** at the bottom, pick the target channel, and authorize
5. Copy the generated webhook URL:
   ```
   https://hooks.slack.com/services/Txxxxxxx/Bxxxxxxx/xxxxxxxxxxxxxxxxxxxxxxxx
   ```

Each webhook is bound to one channel. Because Rudder enables a single active notification provider at a time, only one channel can be configured concurrently.

### 2. Security notes
A Slack Incoming Webhook URL is itself a credential — **anyone holding it can post to the bound channel**. Therefore:

- Never commit the webhook URL to public repositories or attach it in issue screenshots
- If you suspect a leak, **Revoke** the webhook in the Slack App console and generate a new one

Slack does not offer signed payloads or IP allowlists on incoming webhooks; revocation is the only mitigation after leakage.

### 3. Fill the form
| Field | Value |
|:---|:---|
| Webhook URL | The full webhook URL copied above |

Saving takes effect immediately.

Triggered events and runtime behaviour are documented in the platform-level [notification.md](../../../../../docs/notification.md).
