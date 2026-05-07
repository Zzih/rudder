---
description: Kissflow workflow integration, enterprise approval platform
---

## Kissflow Approval Setup

### 1. Get an API Key
1. Sign in to the [Kissflow](https://kissflow.com/) admin console
2. Go to **Settings → API & Integrations**
3. Generate an API Key and copy it

### 2. Get the Account ID
1. Your Kissflow URL has the form `https://{account_id}.kissflow.com/`
2. Copy the `{account_id}` part

### 3. Create the approval process
1. Go to **Process → Create Process**
2. Design the form:
   - Add a `Title` field (text)
   - Add a `Description` field (multi-line text)
3. Configure approval steps:
   - Add approval nodes and assign approvers (fixed or dynamic)
   - Multi-level approval is driven by Kissflow itself; add as many nodes as needed
4. Publish the process
5. Copy the **Process ID** from the process detail page

### 4. Configure the webhook (callback for approval results)
1. Open **Webhooks** in the process settings
2. Add a webhook URL: `https://your-domain/api/approvals/callback/KISSFLOW`
3. Subscribe to `Instance Approved` and `Instance Rejected` events

### 5. Approval initiator
The system uses the current user's email as the Kissflow process initiator when an approval is published.
- Make sure the user's email in Rudder matches their Kissflow account
- If a user has no email configured, the API Key's default account is used

### 6. Fill in the configuration
Paste the values above into the form below and save. Once saved, publishing a workflow will create approval instances in Kissflow automatically.
