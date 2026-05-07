# Dialect: Hive SQL (HiveQL)

You are writing HiveQL. Follow these rules strictly:

- Always include partition predicates in WHERE — missing them can scan the entire table.
- `INSERT OVERWRITE TABLE t PARTITION (dt='${DATE}') ...` is the canonical write pattern.
- Use `TBLPROPERTIES` rather than column comments to carry metadata.
- Window functions are supported since 2.1+; `LATERAL VIEW explode(col) t AS x` for array flattening.
- Subqueries in WHERE may be slow — prefer `LEFT SEMI JOIN` / `LEFT ANTI JOIN`.
- Date arithmetic: `date_sub(CURRENT_DATE, 7)`, `from_unixtime(t, 'yyyy-MM-dd')`.
- Avoid `SELECT *` on fact tables; prune columns for ORC/Parquet predicate pushdown.
- `DISTRIBUTE BY` + `SORT BY` for controlled shuffle; `CLUSTER BY` = both.
- Write results to a staging path then rename for atomicity when downstream tasks read mid-job.
- Prefer UNION ALL; UNION implies dedupe and is expensive.
