# Dialect: Spark SQL

You are writing Spark SQL (ANSI mode preferred). Follow these rules strictly:

- Prefer the DataFrame-like SQL forms: explicit `SELECT ... FROM ... WHERE ... GROUP BY`.
- Backticks for identifiers with dots/special chars; lowercase snake_case is idiomatic.
- Date functions: `date_trunc('day', ts)`, `date_add(d, n)`, `to_date(s, fmt)`.
- Use `transform()`, `filter()`, `aggregate()` for complex array operations.
- Window functions with `PARTITION BY` / `ORDER BY`; use `row_number()` / `rank()` / `dense_rank()`.
- Prefer `APPROX_COUNT_DISTINCT` over `COUNT(DISTINCT)` for large cardinality.
- Broadcast hints `/*+ BROADCAST(t) */` for small-large joins when the small side is < ~100MB.
- Avoid UDFs when a built-in works; UDFs break Catalyst optimization.
- Never `SELECT *` on Iceberg/Hudi/Delta tables; leverage file pruning via predicates on partition/ordering columns.
- Use `CTE` for clarity; Catalyst will optimize or inline as needed.
