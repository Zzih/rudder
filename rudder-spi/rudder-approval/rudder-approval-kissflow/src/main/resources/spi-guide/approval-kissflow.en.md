---
description: Kissflow workflow integration, enterprise approval platform
---

## Kissflow Approval Setup

### 1. Create a Service Account and Access Key

The Kissflow API authenticates with an Access Key (an ID + Secret pair) sent in request headers. External integrations should use the Access Key of a **Service Account** (a non-user account) rather than a personal Access Key: personal credentials stop working once that user is deactivated or deleted, which breaks the integration. Creating a Service Account requires Super Admin or IAM Admin privileges.

1. Click your profile picture and go to **Account Administration → Service account**, enter a name (under 80 characters), and create it
2. Under the Service Account's **Access keys** tab, click **Create access key**, name it, and set an expiry (6 months to 2 years)
3. Copy the **Access Key ID** and **Access Key Secret**; the secret is shown only at creation time

### 2. Get the Account ID

Your Kissflow URL has the form `https://{account_id}.kissflow.com/`. The `{account_id}` segment is the Account ID.

### 3. Create the approval process

1. Go to **Process → Create Process**
2. Design the form fields (**field IDs must match the conventions below exactly** — Rudder writes by these IDs; a field ID is set at creation, cannot be changed after publishing, and cannot contain spaces; the display name may differ):
   - Title field (single-line text), field ID `Title`
   - Content field (multi-line text), field ID `Description`
   - Applicant field (People), field ID `Applicant`
   - For each stage you use, add a **People** field for that stage's candidate approvers, with the **field ID equal to the stage identifier**: `PROJECT_OWNER` / `WORKSPACE_OWNER` / `SUPER_ADMIN`
3. Configure the approval steps: add one approval node per stage, ordered `PROJECT_OWNER → WORKSPACE_OWNER → SUPER_ADMIN`
   - Set each node's approver to "dynamic / based on a field", pointing at that stage's People field
   - Add a condition to each node: **skip the node when its People field is empty**
4. Publish the process
5. Copy the **Process ID** from the process detail page
6. Under **Manage → members/permissions**, add the Service Account from step 1 as a member of this process with create permission. A Service Account can only access processes shared with it; without this, creating instances with its Access Key is rejected

> **How it works**: Rudder computes this request's stage chain and each stage's candidates by resource type, writes the candidate emails into the matching People fields, and creates the instance. Multi-level routing runs inside Kissflow; Rudder only creates the instance and receives the final result (approved/rejected). The built-in level-by-level routing is not used.
>
> Stage chains vary in length across requests (e.g. a project publish may need a single level, while an MCP token request needs "workspace owner → platform admin"), but the workflow nodes are fixed. That is why each node needs a "skip when field empty" condition: Rudder fills only the stage fields needed for this request, leaving the rest empty so their nodes are skipped. Ordering the three nodes as above covers every current approval scenario.

### 4. Stage field naming convention

Rudder integrates by **convention**, with no field mapping configured on the Rudder side: the stage identifier is used directly as the People field ID. Stage identifiers are a fixed (closed) set:

- `PROJECT_OWNER` — project owner
- `WORKSPACE_OWNER` — workspace owner
- `SUPER_ADMIN` — platform administrator (second-level approval for sensitive requests such as MCP tokens and data permissions)

Create a same-named People field (field ID equal to the stage identifier) only for the stages your process actually triggers; stages you never use need no field. If a stage has no matching field, its candidates are not written to Kissflow and that node is left without an approver.

### 5. Approval initiator

Rudder writes the current user's email into the `Applicant` People field from step 3, so the process can reference it where needed (e.g. routing to the applicant's manager via the node formula `Applicant.Manager`).

- Make sure the user's email in Rudder matches their Kissflow account
- The instance creator (`_created_by`) is always the Service Account behind the Access Key, not the applicant; the applicant identity is carried only through the `Applicant` field

### 6. Configure the approval-result callback

Kissflow does not provide a fixed-format approval-result webhook. Send the result back from inside the process using the HTTP connector.

1. Open **Integrations → New integration**, set the trigger to **Kissflow Process** pointing at this process, and pick the event that fires when the item is approved (flow completed)
2. Add an **HTTP → Make an HTTP call (POST)** action:
   - URL: `https://your-domain/api/approvals/callback/KISSFLOW`
   - Header: `Content-Type: application/json`
   - Body (raw / JSON), mapping `instanceId` to the item's Instance ID variable and `approver` to the approver (optional):

   ```json
   {"instanceId": "<Instance ID variable>", "action": "APPROVED", "approver": "<approver>"}
   ```
3. Create a second integration triggered by the rejection event, with `action` set to the fixed value `REJECTED`
4. Save and activate the integrations

Rudder looks up the approval record by `instanceId` and resolves the outcome from `action` (`APPROVED` / `REJECTED`). The `instanceId` must match the Instance ID returned when the instance was created. Ensure the callback fires when the process reaches a terminal state.

### 7. Fill in the configuration

Paste the values above into the form below and save. Once saved, publishing a workflow will create approval instances in Kissflow automatically.
