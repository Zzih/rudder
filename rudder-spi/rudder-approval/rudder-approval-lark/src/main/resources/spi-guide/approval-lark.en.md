---
description: Lark / Feishu approval open platform integration
---

## Lark Approval Setup

### 1. Create a Lark app
1. Open the [Lark Open Platform](https://open.feishu.cn/) and enter the developer console
2. Click "Create Custom App", fill in name and description
3. Get **App ID** and **App Secret** from the "Credentials & Basic Info" page

### 2. Enable approval permissions
On the "Permissions" page of the app, search for and enable:
- `approval:approval` — Approval app (read/write, includes creating instances and subscribing to events)
- `contact:user.id:readonly` — Resolve user ID by email/phone (used to look up the approval initiator)

Submit the request and wait for the admin to approve it.

### 3. Create the approval definition
1. Open the Lark admin console (with developer mode on):
   [Lark Admin Console](https://www.feishu.cn/approval/admin/approvalList?devMode=on)
2. Click "Create Approval"
3. **Configure form widgets** (widget IDs are visible only in developer mode):
   - Add a "single-line text" widget → used to render the approval title (e.g. "Project Publish: xxx")
   - Add a "multi-line text" widget → used to render the publish details (workflow list, etc.)
   - Record both **widget IDs** (e.g. `widget-abc123`); they will go into the configuration below
4. **Configure the approval flow**:
   - Multi-level approval is controlled by Lark; add as many approver nodes as needed
   - For "project owner → workspace owner" two-level approval, add two approver nodes
   - Approvers can be "fixed" or "specified at submission time"
5. Publish the definition
6. Get the **Approval Code**: click edit on the definition and copy the `definitionCode` query parameter from the URL

> **Important**: with Lark approval, multi-level routing is controlled by Lark. Rudder only creates the
> instance and consumes the final result (approved / rejected); the built-in PROJECT_OWNER → WORKSPACE_OWNER
> two-step routing is no longer used.

### 4. Configure event subscriptions (callback for approval results)
1. In the app's "Dev Config → Events & Callbacks → Event Config":
   - Choose "Send events to developer server" (Webhook mode)
   - Set the request URL to `https://your-domain/api/approvals/callback/LARK`
   - Lark sends a `url_verification` request; Rudder responds to the challenge automatically
2. In "Add Event", search for and subscribe to: "Approval Instance Status Change" (`approval_instance`)
3. Under "Encryption", record the **Encrypt Key** and **Verification Token** (if encryption is enabled)
4. **Publish a new version of the app** for the configuration to take effect
5. Rudder calls the Lark subscription API automatically to activate event delivery (once per approval_code)

> Lark retries failed event deliveries at 15s → 5min → 1h → 6h (up to 4 times). Rudder handles this idempotently.

### 5. Approval initiator
When an approval is published, Rudder takes the current user's email and resolves it to an `open_id` via the Lark contacts API to use as the initiator.
- Make sure each user's email matches their Lark account
- If a user has no email configured, publishing the approval will fail

### 6. Fill in the configuration
Paste the values above into the form below and save. Once saved, publishing a workflow will create approval instances in Lark automatically.
