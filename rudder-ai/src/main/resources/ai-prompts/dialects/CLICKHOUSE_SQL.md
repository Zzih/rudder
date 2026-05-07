# Dialect: ClickHouse SQL

You are writing ClickHouse SQL (23+). Column-store MPP — semantics differ significantly from MySQL/PostgreSQL. Follow these rules strictly:

- Identifiers use backticks `` ` `` or double quotes (configurable). String literals use single quotes.
- Pagination: `LIMIT n OFFSET m` works but **OFFSET on huge result sets is expensive** (no index seek). For top-N queries use `ORDER BY ... LIMIT n` directly without OFFSET.
- **Engine matters**: `MergeTree` family (default for analytics), `ReplacingMergeTree` (dedup by ORDER BY key), `SummingMergeTree` (auto pre-aggregate), `AggregatingMergeTree` (state-based agg). Pick by use case, propose engine choice in DDL.
- `ORDER BY` clause in `CREATE TABLE` is the **primary key + sort order** — not a query clause. Choose ORDER BY columns for typical predicate access patterns.
- Partition by date typically `PARTITION BY toYYYYMM(date_col)` or `toDate(date_col)`. Always include partition predicate in WHERE for big tables.
- `FINAL` modifier: `SELECT ... FROM table FINAL` forces merge-on-read for `ReplacingMergeTree` / `CollapsingMergeTree` — **expensive**, avoid in hot paths; prefer agg with `argMax` / `groupArray` semantics.
- Aggregate functions are first-class: `uniq()` (HLL), `uniqExact()`, `quantile()`, `quantileTDigest()`, `groupArray()`, `argMax()`, `topK()`. Use the right one for the right size/accuracy tradeoff.
- Date / time:
  - `now()`, `today()`, `yesterday()`
  - `toStartOfDay(col)` / `toStartOfMonth(col)` / `toYYYYMM(col)` / `toDate(col)`
  - `dateDiff('day', start, end)`, `addDays(col, 7)`
  - Format: `formatDateTime(col, '%Y-%m-%d')`
- Arrays are native + extensively used: `arrayMap`, `arrayFilter`, `arrayJoin` (= UNNEST), `groupArray`. Often replace JOIN with array operations for perf.
- `INSERT INTO ... VALUES` is fine for small batches; for high throughput use bulk INSERT with `Native` / `RowBinary` formats (out of SQL).
- **No transactions across multiple statements** (only INSERT-level atomicity). No referential integrity constraints (no FK).
- Joins: ClickHouse re-broadcasts the right table by default — put **smaller** table on the right. Use `GLOBAL JOIN` for distributed setups.
- `EXPLAIN PLAN` / `EXPLAIN PIPELINE` for query inspection.
- Avoid `SELECT *` — column pruning is the #1 optimization for column store.
