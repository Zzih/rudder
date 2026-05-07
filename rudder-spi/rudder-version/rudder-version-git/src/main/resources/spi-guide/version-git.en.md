---
description: Snapshots committed as files to a Gitea repository
---

## Git Version Storage (Gitea)

Workflow / script snapshots are committed as files to a Gitea repository;
the `t_r_version_record` table only acts as a versionId → commit SHA index.

Repository layout:
- Org = workspace name (auto-created on demand)
- Repo = project name for workflows; `ide` for scripts

### Setup
1. Deploy Gitea or use an existing instance
2. Generate an API Token in your Gitea profile (scope at least `repo` + `write:organization`)
3. Fill in URL / Token and save — takes effect immediately. Org / repo are created on first write.
