---
description: 本地 MySQL FULLTEXT(ngram 关键词匹配),零部署成本
---

## 本地向量存储(LOCAL / MySQL FULLTEXT)

不部署独立向量库,直接用 MySQL 的 FULLTEXT ngram 索引做关键词召回。

- **优点**:零部署成本,`t_r_ai_document` 表自带 FULLTEXT 索引
- **缺点**:只有关键词匹配,无语义理解。同义词 / 业务专名召回效果差
- **何时选**:文档量少(< 500 条),或部署环境不允许额外中间件

无参数。选中此 provider 后 `search_documents` 会走 MySQL
`MATCH(title, content) AGAINST(? IN NATURAL LANGUAGE MODE)` 检索。
