# 贡献指南

[English](CONTRIBUTING.en.md) | 中文

欢迎贡献！无论是 bug 修复、新功能、文档还是讨论参与，都很受欢迎。

## 提交 Issue

- **Bug**：复现步骤、Rudder 版本、JDK 版本、关键日志/堆栈
- **Feature**：使用场景、期望行为；和 maintainer 在 issue 里对齐设计后再写代码,减少返工

## Pull Request 流程

GitHub 上 Rudder 的主分支是 **`dev`**（不是 `main`）。所有 PR 请开到 `dev`。

> 背景：Rudder 主开发仓位于私有 Gitea 的 `main` 分支,通过 sync 脚本批次同步到 GitHub `dev`。GitHub 上看到的 `dev` 就是公开主分支。`main` 分支在 GitHub 上不存在。

流程：

1. Fork 仓库到自己账号
2. 基于 `dev` 切分支：`git checkout -b feat/xxx dev`
3. 改代码 + 跑本地校验（见下）
4. 推到 fork: `git push origin feat/xxx`
5. 在 GitHub 上开 PR,base = `Zzih/rudder:dev`,head = 你的分支

PR 描述请说明：**改了什么 / 为什么改 / 怎么测的**。一个 PR 一件事，不混杂多个不相关改动。

## 本地构建

依赖：

- JDK 21
- Node.js 20+
- Maven 通过 `./mvnw` 自带,不用单独装
- 跑起来还要 MySQL 8 + Redis 7+,详见 [README - 快速开始](README.md#快速开始)

构建：

```bash
git clone https://github.com/Zzih/rudder.git
cd rudder
./mvnw clean install -DskipTests
```

产物：`rudder-dist/target/rudder-<版本>-bin.tar.gz`。

## 代码风格

Rudder 用 [Spotless](https://github.com/diffplug/spotless) 统一 Java 风格（格式 + import 顺序 + license header）。**没有 checkstyle**。

### 自动修复格式

```bash
./mvnw spotless:apply
```

### 提交时自动校验（推荐）

我们提供 [`.pre-commit-config.yaml`](.pre-commit-config.yaml)，配合 [pre-commit 框架](https://pre-commit.com/) 使用：

```bash
# 一次性安装(全机)
brew install pre-commit          # macOS
# pip install pre-commit         # Linux / 其他

# 在仓库里激活(一次性)
pre-commit install
```

之后每次 `git commit` 会自动跑 `./mvnw spotless:check`，不通过就拦下。

### 不装 pre-commit 也行

`./mvnw compile` / `./mvnw package` 都会触发 spotless check —— 本地走一遍 build 就能发现问题。

### 前端

```bash
cd rudder-ui
npm run lint        # 自动 fix ESLint
npm run prettier    # 自动 format
```

## Commit Message 约定

走 [Conventional Commits](https://www.conventionalcommits.org/) 风格，格式：

```
<type>(<scope>): <subject>
```

- **type**：`feat` / `fix` / `refactor` / `style` / `docs` / `chore` / `build` / `test` / `perf`
- **scope**：模块名简称，例如 `ide`、`execution`、`docker`、`ai`、`spi-llm`
- **subject**：一句话说明改了什么，中英文都可以

例子：

```
fix(execution): IDE 直跑 params 落库格式对齐 List<Property>
feat(ide): 查询结果支持下载 CSV/Excel
chore(docker): multi-stage 瘦身镜像 ~850MB
```

## License

Rudder 使用 [Apache License 2.0](LICENSE)。提交 PR 即表示你同意按此协议授权你的代码。
