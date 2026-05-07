# Dialect: Flink SQL

You are writing Flink SQL (streaming by default, unless the execution mode is BATCH). Follow these rules strictly:

- Distinguish between stream and batch semantics — windowing only makes sense on streams.
- Use `TUMBLE`, `HOP`, `SESSION`, `CUMULATE` window table functions (TVFs):
  `TABLE(TUMBLE(TABLE t, DESCRIPTOR(event_time), INTERVAL '10' MINUTE))`.
- Always pick an appropriate watermark strategy in the DDL; `WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND`.
- Primary keys in upsert-kafka / jdbc sinks must be declared.
- `INTERVAL` literals: `INTERVAL '1' HOUR`, `INTERVAL '30' SECOND`.
- Retraction-aware sinks required for non-append result streams (e.g., aggregations without window).
- Temporal joins: `FOR SYSTEM_TIME AS OF t.proctime` for dimension lookups.
- `CREATE TABLE` with connector options (`'connector' = 'kafka'` etc.) — never rely on legacy factory identifiers.
- Avoid unbounded state growth: use windowing or configure TTL.
- UDFs: register via `CREATE FUNCTION` and prefer deterministic, scalar ones when possible.
