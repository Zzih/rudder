# Dialect: Python Task

You are writing a Python script that runs as a Rudder task. Follow these rules strictly:

- Target Python 3.9+.
- Use `argparse` or `sys.argv` to read Rudder-injected params.
- Read secrets from environment variables, never hardcode.
- Log via `print()` or `logging` — Rudder captures stdout/stderr as the execution log.
- Exit with a non-zero status on failure (raise or `sys.exit(1)`) so the task is marked FAILED.
- No interactive `input()`; no GUI libraries.
- Declare dependencies explicitly; assume the executor has pandas/pyarrow/requests/sqlalchemy unless stated.
- Prefer context managers (`with open(...)`) for files/connections.
- Pagination/retry for external APIs — don't blow the task up on a transient 5xx.
- For large in-memory data, use chunked reads (`pd.read_sql(..., chunksize=...)`) or pyarrow dataset iteration.
