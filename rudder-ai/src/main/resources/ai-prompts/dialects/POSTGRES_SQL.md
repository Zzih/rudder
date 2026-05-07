# Dialect: PostgreSQL

You are writing PostgreSQL (12+). Follow these rules strictly:

- Identifiers use double quotes `"col"`; string literals use single quotes. Escape single quote by doubling: `'it''s'`.
- Pagination: `LIMIT n OFFSET m` or SQL-standard `OFFSET m ROWS FETCH NEXT n ROWS ONLY`. For deep pagination prefer cursor-style `WHERE id > last_seen ORDER BY id LIMIT n`.
- CTEs `WITH ...`, recursive CTEs `WITH RECURSIVE`, window functions, lateral joins (`LATERAL`) all fully supported.
- Date / time:
  - `NOW()` (with timezone) vs `CURRENT_TIMESTAMP` (alias) vs `LOCALTIMESTAMP` (no tz)
  - `DATE_TRUNC('month', col)`, `EXTRACT(YEAR FROM col)`
  - Interval arithmetic: `col - INTERVAL '7 days'`, `col + INTERVAL '1 month'`
  - `TO_CHAR(col, 'YYYY-MM-DD')` for formatting
- Upsert: `INSERT ... ON CONFLICT (col) DO UPDATE SET ...` — **not** `INSERT IGNORE` / `REPLACE`.
- `RETURNING` clause: `INSERT/UPDATE/DELETE ... RETURNING id, ...` returns affected rows; use this instead of separate SELECT round-trips.
- Arrays: native `int[]` / `text[]`; functions like `ANY(arr)` / `ALL(arr)` / `unnest(arr)` / `array_agg()`.
- JSON / JSONB: prefer `jsonb` (binary, indexable) over `json`. Operators `->`, `->>`, `@>`, `?`. GIN index for jsonb queries.
- **Avoid `SELECT *`** — column pruning + reduces serialization overhead.
- Indexes: B-tree by default; GiST for geo / range; GIN for full-text / arrays / jsonb. Partial indexes (`WHERE active = true`) for hot subsets.
- `EXPLAIN (ANALYZE, BUFFERS)` to verify plans on large queries.
- Schemas: prefer fully qualifying `schema.table` to avoid `search_path` ambiguity.
- Type casting: `col::integer` (Postgres-specific shorthand) or `CAST(col AS integer)` (standard).
