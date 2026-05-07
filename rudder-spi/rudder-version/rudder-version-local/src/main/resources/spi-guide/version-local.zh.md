---
description: 快照直接写入 MySQL(默认方案,无需额外配置)
---

## 本地版本存储（默认）

版本快照直接保存在 MySQL 的 `t_r_version_record` 表中，无需额外依赖。
适合默认部署与单机 / 小团队场景。
