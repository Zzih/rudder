# 贡献指南

[English](CONTRIBUTING.en.md) | 中文

本文档规定向 Rudder 提交 Issue 与 Pull Request 的流程,涵盖本地构建、代码风格、提交信息约定与许可证条款。

## 提交 Issue

- **缺陷报告**:须提供复现步骤、Rudder 版本、JDK 版本、相关日志或堆栈信息。
- **功能请求**:须说明使用场景与期望行为。涉及较大设计调整的改动,请先在 Issue 中与维护者讨论并达成共识,再着手实现。

## Pull Request 流程

项目主分支为 `dev`。所有 Pull Request 须以 `dev` 为目标分支。

提交流程如下:

1. Fork 仓库至个人账户。
2. 基于 `dev` 创建特性分支:`git checkout -b feat/xxx dev`。
3. 提交代码前,完成本地校验,参见 [本地构建](#本地构建) 与 [代码风格](#代码风格)。
4. 推送至 fork 仓库:`git push origin feat/xxx`。
5. 在 GitHub 创建 Pull Request,base 设为 `Zzih/rudder:dev`,head 设为特性分支。

Pull Request 描述须包含变更内容、动机及验证方式。单个 Pull Request 应聚焦单一目的,不应包含多项无关改动。

## 本地构建

### 环境依赖

- JDK 21
- Node.js 20 及以上版本
- Maven 由项目内置的 `./mvnw` 提供,无需独立安装。
- 运行时另需 MySQL 8 与 Redis 7 及以上版本。详细配置参见 [README 快速开始](README.md#快速开始)。

### 构建命令

```bash
git clone https://github.com/Zzih/rudder.git
cd rudder
./mvnw clean install -DskipTests
```

构建产物位于 `rudder-dist/target/rudder-<版本>-bin.tar.gz`。

## 代码风格

Java 代码风格由 [Spotless](https://github.com/diffplug/spotless) 统一管理,涵盖格式化、import 顺序与 license header。项目不使用 Checkstyle。

### 应用格式化

```bash
./mvnw spotless:apply
```

### 提交时自动校验

仓库提供 [`.pre-commit-config.yaml`](.pre-commit-config.yaml),配合 [pre-commit](https://pre-commit.com/) 框架启用提交时校验。

跨仓库一次性安装 pre-commit:

```bash
brew install pre-commit          # macOS
# pip install pre-commit         # Linux 或其他平台
```

在仓库根目录激活 hook:

```bash
pre-commit install
```

完成上述配置后,每次 `git commit` 将自动执行 `./mvnw spotless:check`。校验失败将阻止本次提交。

### 替代方案

未启用 pre-commit 时,执行 `./mvnw compile` 或 `./mvnw package` 亦会触发 Spotless 校验。

### 前端

```bash
cd rudder-ui
npm run lint        # ESLint 自动修复
npm run prettier    # Prettier 格式化
```

## Commit Message 约定

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范,格式:

```
<type>(<scope>): <subject>
```

- **type**:`feat` / `fix` / `refactor` / `style` / `docs` / `chore` / `build` / `test` / `perf`
- **scope**:相关模块名缩写,例如 `ide`、`execution`、`docker`、`ai`、`spi-llm`
- **subject**:本次变更的简要说明,支持中英文

示例:

```
fix(execution): IDE 直跑 params 落库格式对齐 List<Property>
feat(ide): 查询结果支持下载 CSV/Excel
chore(docker): 多阶段构建,镜像体积减少约 850MB
```

## License

Rudder 采用 [Apache License 2.0](LICENSE) 许可。提交 Pull Request 即视为同意以相同条款授权所贡献的代码。
