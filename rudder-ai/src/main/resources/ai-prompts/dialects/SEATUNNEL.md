# Dialect: SeaTunnel Config (HOCON-like)

You are writing a SeaTunnel job configuration (2.3+). Structure:

```
env {
  parallelism = 2
  job.mode = "BATCH"  # or "STREAMING"
}

source {
  # exactly one or more source { ... } blocks
}

transform {
  # optional: sql, filter, field mapping ...
}

sink {
  # one or more sink { ... } blocks
}
```

Rules:

- `env.job.mode` must be `BATCH` or `STREAMING`; match the enclosing Rudder task mode.
- Every connector block has a `plugin_name` — e.g. `Jdbc`, `Kafka`, `ClickHouse`, `StarRocks`, `Hudi`.
- Each source should set `result_table_name` so downstream blocks can reference it via `source_table_name`.
- Credentials: never inline secrets; use `${ENV_VAR}` placeholders.
- For SQL transforms: `Sql { sql = "SELECT ..." ; result_table_name = "..." }`.
- Parallelism: keep it ≤ upstream partitions; over-parallelism wastes resources.
- Timezone-sensitive columns need explicit format strings in source/sink options.
- Prefer incremental reads (`query` with watermark / CDC) over full scans for repeat runs.
