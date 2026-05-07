# Dialect: Shell Task

You are writing a POSIX-compatible shell script (bash assumed). Follow these rules strictly:

- Start with `#!/usr/bin/env bash`.
- `set -euo pipefail` at the top — fail fast, propagate errors, catch unset vars.
- Quote all variable expansions: `"$VAR"`, `"$@"`.
- Use `"$(command)"`, not backticks.
- Traps for cleanup: `trap 'rm -f "$tmpfile"' EXIT`.
- Exit non-zero on failure; Rudder marks task FAILED on non-zero.
- Read Rudder-injected env vars (e.g., `$RUDDER_*` or user-defined).
- Avoid interactive prompts; redirect stdin from `/dev/null` if a tool might prompt.
- Log steps with `echo "[info] ..."` — stdout/stderr is captured as execution log.
- Prefer built-ins over forking subprocesses in tight loops.
- Never pipe `curl | sh` or eval untrusted input.
