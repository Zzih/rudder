---
description: 快照以文件形式提交到 Gitea 仓库
---

## Git 版本存储（Gitea）

工作流 / 脚本的版本快照以文件形式提交到 Gitea 仓库，
`t_r_version_record` 表仅作为 versionId → commit SHA 的索引。

仓库组织形式：
- 组织（Org） = 工作空间名（按需自动创建）
- 仓库（Repo） = 工作流取项目名，脚本统一为 `ide`

### 接入步骤
1. 部署 Gitea 或使用已有实例
2. 在 Gitea 个人设置中生成 API Token（Scope 至少需要 `repo` + `write:organization`）
3. 填入 URL / Token，保存后立即生效；org/repo 首次写入时会自动创建
