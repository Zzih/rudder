# Contributing Guide

English | [中文](CONTRIBUTING.md)

This document specifies the procedure for submitting issues and pull requests to Rudder, including local build, code style, commit message convention, and license terms.

## Filing Issues

- **Bug reports**: provide reproduction steps, Rudder version, JDK version, and relevant logs or stack traces.
- **Feature requests**: describe the use case and expected behavior. For substantial design changes, propose the approach in an issue and reach consensus with maintainers before implementation.

## Pull Request Workflow

The primary branch is `dev`. All pull requests must target `dev`.

Procedure:

1. Fork the repository to a personal account.
2. Create a feature branch from `dev`: `git checkout -b feat/xxx dev`.
3. Implement the changes and complete local checks before submission. See [Local Build](#local-build) and [Code Style](#code-style).
4. Push the branch to the fork: `git push origin feat/xxx`.
5. Open a pull request on GitHub with base `Zzih/rudder:dev` and head set to the feature branch.

Pull request descriptions must include the scope of the change, the motivation, and the verification approach. A pull request shall focus on a single purpose and shall not bundle unrelated changes.

## Local Build

### Requirements

- JDK 21
- Node.js 20 or later
- Maven is provided via the bundled `./mvnw` wrapper; a separate installation is not required.
- The runtime platform additionally requires MySQL 8 and Redis 7 or later. See [README Quick Start](README.en.md#quick-start) for configuration details.

### Build command

```bash
git clone https://github.com/Zzih/rudder.git
cd rudder
./mvnw clean install -DskipTests
```

The build artifact is produced at `rudder-dist/target/rudder-<version>-bin.tar.gz`.

## Code Style

Java code style is governed by [Spotless](https://github.com/diffplug/spotless), covering formatting, import ordering, and license headers. Checkstyle is not used.

### Apply formatting

```bash
./mvnw spotless:apply
```

### Automatic check on commit

The repository provides [`.pre-commit-config.yaml`](.pre-commit-config.yaml) for use with the [pre-commit](https://pre-commit.com/) framework.

Install pre-commit once per machine:

```bash
brew install pre-commit          # macOS
# pip install pre-commit         # Linux or other platforms
```

Activate the hook in the repository:

```bash
pre-commit install
```

Once activated, `./mvnw spotless:check` runs on every `git commit`. The commit is rejected if validation fails.

### Alternative

When pre-commit is not configured, executing `./mvnw compile` or `./mvnw package` also triggers the Spotless check.

### Frontend

```bash
cd rudder-ui
npm run lint        # ESLint auto-fix
npm run prettier    # Prettier format
```

## Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>
```

- **type**: `feat` / `fix` / `refactor` / `style` / `docs` / `chore` / `build` / `test` / `perf`
- **scope**: short module name, for example `ide`, `execution`, `docker`, `ai`, `spi-llm`
- **subject**: a concise summary of the change. Both English and Chinese are accepted.

Examples:

```
fix(execution): align IDE direct-run params storage with List<Property>
feat(ide): support CSV/Excel download for query results
chore(docker): multi-stage build, image size reduced by ~850MB
```

## License

Rudder is licensed under [Apache License 2.0](LICENSE). Submitting a pull request constitutes agreement to license the contribution under the same terms.
