# Dialect: Apache Doris SQL

You are writing Apache Doris SQL (2.x+). Doris uses MySQL wire protocol but is an MPP OLAP engine — semantics differ from MySQL. Follow these rules strictly:

- Identifiers can use backticks `` ` `` (MySQL-compatible). String literals use single quotes.
- Pagination: `LIMIT n OFFSET m`. For deep pagination on large result sets prefer `WHERE id > last_seen_id ORDER BY id LIMIT n` (cursor) over high `OFFSET`.
- CTEs `WITH ...` and window functions are fully supported.
- **Aggregate / Duplicate / Unique key models** decide write semantics — partial column updates only on Unique-key tables; aggregate-key tables auto-merge by REPLACE / SUM / MAX / MIN / BITMAP_UNION etc.
- Bitmap / HLL columns: use `BITMAP_UNION_COUNT(bitmap_col)` and `HLL_UNION_AGG(hll_col)` for distinct count, **never** `COUNT(DISTINCT ...)` on huge bitmaps — bitmap is the whole point.
- Partitioning: usually `RANGE(date_col)` or `LIST(region)`. Always include partition predicate in WHERE on partitioned tables (`dt >= '2024-01-01'`) to enable partition pruning.
- Use `BROADCAST` / `SHUFFLE` join hints only after confirming via `EXPLAIN` that the optimizer picked wrong.
- Materialized views (`CREATE MATERIALIZED VIEW`) and rollup tables can dramatically speed up aggregations on Aggregate-key tables — propose them when seeing repeated heavy GROUP BY.
- Date functions: `DATE_FORMAT(dt, '%Y-%m-%d')`, `DATE_SUB(CURDATE(), INTERVAL 7 DAY)`, `UNIX_TIMESTAMP(...)`. MySQL-compatible.
- `INSERT INTO ... SELECT` is the standard load. For high throughput use Stream Load / Routine Load (out of SQL). `INSERT INTO ... VALUES` is fine for small writes only.
- Avoid `SELECT *` on wide column-store tables — column pruning is the first-line optimization.
- `SHOW PROCESSLIST` / `SHOW BACKENDS` for cluster diagnostics; `EXPLAIN` and `EXPLAIN VERBOSE` for plan inspection.
