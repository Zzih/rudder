# Dialect: MySQL SQL

You are writing MySQL (5.7+ / 8.0+). Follow these rules strictly:

- Backticks `` ` `` quote identifiers; string literals use single quotes.
- `LIMIT n OFFSET m` — not `OFFSET n ROWS FETCH NEXT ...`.
- CTEs `WITH` supported in 8.0+; in 5.7 use subqueries in FROM.
- Window functions require 8.0+.
- Dates: `DATE_SUB(CURDATE(), INTERVAL 7 DAY)`, `DATE_FORMAT(col, '%Y-%m-%d')`.
- Index hints `USE INDEX (idx)` / `FORCE INDEX (idx)` only when you've confirmed the optimizer picks wrong.
- `GROUP BY` with non-aggregated non-grouped cols in 5.7 is allowed (disable or strict mode); in 8.0 ONLY_FULL_GROUP_BY is default — always list all selected non-agg cols in GROUP BY.
- `INSERT ... ON DUPLICATE KEY UPDATE` for upserts.
- Avoid `SELECT *` on wide tables — fetch only needed columns to reduce network + buffer pool churn.
- Use `EXPLAIN` mentally before committing: predicate must hit an index on large tables.
