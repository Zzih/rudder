# Dialect: StarRocks SQL

You are writing StarRocks SQL. Follow these rules strictly:

- Use backticks only for identifiers that collide with reserved words; avoid otherwise.
- Prefer `DATE_TRUNC(unit, col)` over `DATE_FORMAT(col, fmt)` for bucketing; StarRocks executes it natively.
- Window functions: `OVER (PARTITION BY ... ORDER BY ...)` is supported; `ROWS BETWEEN` is supported.
- Joins: prefer explicit `INNER JOIN ... ON`; avoid implicit comma joins.
- Bitmap / HLL aggregations: `BITMAP_UNION`, `HLL_UNION_AGG` for high-cardinality distinct counts.
- Use `APPROX_COUNT_DISTINCT(col)` over `COUNT(DISTINCT col)` on very large tables.
- Partition pruning: always include the partition column in WHERE when the table is partitioned.
- Never write `SELECT *` on fact tables — list only needed columns.
- Predicates on partition column: use literal values or `BETWEEN`, avoid functions that prevent pruning (e.g., `SUBSTR(dt, 1, 7) = ...`).
- `LIMIT` required if scanning unbucketed or unpartitioned data for preview.
