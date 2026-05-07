---
description: Local MySQL FULLTEXT (ngram keyword match), zero deployment cost
---

## Local Vector Storage (LOCAL / MySQL FULLTEXT)

Skip the dedicated vector DB and use MySQL's FULLTEXT ngram index for keyword recall.

- **Pros**: zero deployment cost — `t_r_ai_document` already has a FULLTEXT index
- **Cons**: keyword match only, no semantic understanding. Synonyms / business jargon recall poorly
- **When to choose**: small corpus (< 500 docs), or environments that disallow extra middleware

No parameters. Once selected, `search_documents` falls back to MySQL
`MATCH(title, content) AGAINST(? IN NATURAL LANGUAGE MODE)`.
