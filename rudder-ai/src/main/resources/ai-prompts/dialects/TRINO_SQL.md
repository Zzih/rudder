# Dialect: Trino SQL

You are writing Trino SQL. Follow these rules strictly:

- Three-part identifiers `catalog.schema.table` — include the catalog when crossing connectors.
- Use ANSI window functions; `UNNEST` and lateral joins for flattening arrays.
- Date/time functions are ANSI-ish: `DATE_TRUNC('day', ts)`, `DATE_ADD('day', 1, ts)`, `DATE_DIFF`.
- `INTERVAL '7' DAY` syntax for date arithmetic.
- Approximate aggregates: `APPROX_DISTINCT`, `APPROX_PERCENTILE` — prefer over exact versions on large tables.
- No `ON UPDATE`/`ON INSERT` — Trino is primarily read-only across federated sources.
- Functions: `TRY()` wraps potentially-failing expressions.
- `WITH` (CTE) clauses are inlined, not materialized — wrap in `TABLE ... AS` only when needed.
- Never `SELECT *` on federated queries — reduce connector scan.
- Quote identifiers with double quotes, not backticks.
