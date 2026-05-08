# Contributing Guide

English | [中文](CONTRIBUTING.md)

Contributions are welcome — bug fixes, features, docs, or just discussion.

## Filing Issues

- **Bug**: reproduction steps, Rudder version, JDK version, relevant logs/stack
- **Feature**: use case, expected behavior; align with maintainers in the issue first before writing code, to avoid rework

## Pull Request Workflow

The primary branch on GitHub is **`dev`** (not `main`). Open all PRs against `dev`.

> Background: Rudder is developed on a private Gitea repo's `main` branch and batch-synced to GitHub `dev` via a sync script. The `dev` branch on GitHub is the public-facing primary. `main` does not exist on GitHub.

Steps:

1. Fork the repo
2. Branch off `dev`: `git checkout -b feat/xxx dev`
3. Code + run local checks (see below)
4. Push to your fork: `git push origin feat/xxx`
5. Open a PR on GitHub, base = `Zzih/rudder:dev`, head = your branch

PR description should cover: **what changed / why / how it was tested**. One PR, one purpose — don't bundle unrelated changes.

## Local Build

Requirements:

- JDK 21
- Node.js 20+
- Maven via `./mvnw` (bundled, no separate install)
- To actually run it: MySQL 8 + Redis 7+. See [README - Quick start](README.en.md#quick-start)

Build:

```bash
git clone https://github.com/Zzih/rudder.git
cd rudder
./mvnw clean install -DskipTests
```

Output: `rudder-dist/target/rudder-<version>-bin.tar.gz`.

## Code Style

Rudder uses [Spotless](https://github.com/diffplug/spotless) for Java style (formatting + import order + license header). **No checkstyle.**

### Auto-fix formatting

```bash
./mvnw spotless:apply
```

### Auto-check on commit (recommended)

We ship a [`.pre-commit-config.yaml`](.pre-commit-config.yaml) for use with the [pre-commit framework](https://pre-commit.com/):

```bash
# One-time install (machine-wide)
brew install pre-commit          # macOS
# pip install pre-commit         # Linux / others

# One-time activation in this repo
pre-commit install
```

Every `git commit` afterward auto-runs `./mvnw spotless:check` and blocks the commit on failure.

### Without pre-commit

`./mvnw compile` / `./mvnw package` both trigger spotless check — running a local build catches the same problems.

### Frontend

```bash
cd rudder-ui
npm run lint        # ESLint with --fix
npm run prettier    # Prettier auto-format
```

## Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>
```

- **type**: `feat` / `fix` / `refactor` / `style` / `docs` / `chore` / `build` / `test` / `perf`
- **scope**: short module name, e.g. `ide`, `execution`, `docker`, `ai`, `spi-llm`
- **subject**: one-line summary; English or Chinese both fine

Examples:

```
fix(execution): align IDE direct-run params storage with List<Property>
feat(ide): support CSV/Excel download for query result
chore(docker): multi-stage build, image down ~850MB
```

## License

Rudder is licensed under [Apache License 2.0](LICENSE). Submitting a PR implies you agree to license your contribution under the same terms.
