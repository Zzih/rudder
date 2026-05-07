# 快速开始

> 目标：从零到登录 Rudder、连第一个数据源、跑一个 SQL，约 10 分钟。
> 仅平台基础链路；AI / 工作流编排 / 调度发布等能力在专题文档里展开。

## 前置依赖

| 服务 | 用途 | 必需 |
|:---|:---|:---:|
| Java 21 | 运行时 | ✓ |
| MySQL 8.x | 主库 + 服务注册 | ✓ |
| Redis 6+ | 流取消广播 / 限流 / 缓存 | ✓ |
| Maven 3.9+ | 编译（也可用自带 `./mvnw`） | ✓ |
| Node.js 18+ | 仅前端开发模式需要 | |

启动 MySQL + Redis（Docker 最快）：

```bash
docker run -d --name rudder-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=rudder123 \
  -e MYSQL_DATABASE=rudder \
  -e MYSQL_USER=rudder \
  -e MYSQL_PASSWORD=rudder123 \
  mysql:8

docker run -d --name rudder-redis -p 6379:6379 redis:7
```

## 1. 拉代码并配置 .env

```bash
git clone https://github.com/Zzih/rudder.git && cd rudder
cp .env.example .env
```

`.env` 至少要把三个密钥换成自己的随机串（**每个 ≥ 32 字节**）：

```bash
RUDDER_JWT_SECRET=<32+ 字节随机串>
RUDDER_ENCRYPT_KEY=<32+ 字节随机串>
RUDDER_RPC_AUTH_SECRET=<32+ 字节随机串>
```

> JWT_SECRET 用于登录态签发；ENCRYPT_KEY 用于数据源凭证 AES 加密；RPC_AUTH_SECRET 用于 Server↔Execution RPC 鉴权（两端必须一致）。

数据库连接如非默认值，覆盖 `RUDDER_DB_URL / RUDDER_DB_USERNAME / RUDDER_DB_PASSWORD`。

## 2. 编译并打 tarball

```bash
./mvnw clean package -DskipTests
```

产物：`rudder-dist/target/rudder-<version>-SNAPSHOT.tar.gz`，解压目录形如：

```
rudder-<version>/
├── bin/                 通用脚本（env.sh / start-server.sh / rudder-daemon.sh）
├── api-server/          Server 包（HTTP 5680 / RPC 5690）
└── execution-server/    Execution 包（HTTP 5681 / RPC 5691）
```

> Schema 与种子数据由 Spring Boot 启动时自动执行（`spring.sql.init.mode=always`，加载 `schema.sql + data.sql`），详见 [配置参考](configuration.md#数据库初始化)。

## 3. 启动 Server 与 Execution

```bash
cd rudder-dist/target/rudder-*-SNAPSHOT

# 后台启动
bin/rudder-daemon.sh start all

# 查看状态
bin/rudder-daemon.sh status all

# 停止
bin/rudder-daemon.sh stop all
```

也可分别前台启动（调试用）：

```bash
RUDDER_FOREGROUND=true api-server/start.sh
RUDDER_FOREGROUND=true execution-server/start.sh
```

日志路径：

- `<HOME>/logs/api-server.out` / `<HOME>/logs/execution-server.out`
- 任务执行日志：`<HOME>/logs/tasks/<workflow>/<taskInstanceId>.log`

## 4. 登录

浏览器打开 **http://localhost:5680**，默认管理员：

```
用户名：admin
密码  ：admin123
```

> 默认账号来自 `data.sql`，**生产环境务必先改密码或在 Admin 里禁用**。

## 5. 接入第一个数据源

`管理` → `数据源` → `新建`，以 MySQL 为例：

| 字段 | 示例 |
|:---|:---|
| name | demo-mysql |
| type | MYSQL |
| host / port | 127.0.0.1 / 3306 |
| database | rudder |
| username / password | rudder / rudder123 |

保存后点 **测试连接**。Rudder 用 `RUDDER_ENCRYPT_KEY` 把密码 AES 加密后落 `t_r_datasource`。

> 数据源的 `name` 不可变，参与 DataHub URN / OpenMetadata FQN，改名会断元数据连接。

## 6. 跑第一个 SQL

1. 顶部进入任意 Workspace（默认空间已建好）→ `IDE`
2. 左侧脚本树 `新建脚本` → 类型选 `MYSQL` → 选第 5 步的数据源
3. 编辑器输入：
   ```sql
   SELECT NOW(), VERSION();
   ```
4. `Ctrl/Cmd + Enter` 或点击运行
5. 下方面板看到结果即链路通

## 下一步

| 想做的事 | 看哪 |
|:---|:---|
| 全部环境变量与 yml 字段 | [配置参考](configuration.md) |
| 生产部署 / Docker / 多实例 | [部署指南](deployment.md) |
| 把 SQL 编排成 DAG + 定时调度 | [工作流](workflow.md) |
| 13 种任务类型与各自参数 | [任务类型](task-types.md) |
| Workspace / RBAC / 数据源授权 | [权限模型](permissions.md) |
| 启用 AI 助手 | [AI 模块](ai/README.md) |
| 对接 DolphinScheduler 生产调度 | [DolphinScheduler 集成](dolphinscheduler.md) |
| 让 Claude Desktop / Cursor 直连平台跑 SQL / 工作流 | [MCP](mcp.md) |

## 常见坑

| 症状 | 原因 / 处置 |
|:---|:---|
| 启动报 `RUDDER_RPC_AUTH_SECRET must be at least 32 bytes` | `.env` 没配或长度不足 |
| Execution 连不上 Server | 两边 `RUDDER_RPC_AUTH_SECRET` 不一致；或 RPC 端口被防火墙拦 |
| Server 起来了但 IDE 空白 | 静态资源被 gzip 压缩失败，浏览器禁用了 `application/javascript` 解压；改用 Chrome / Edge |
| 数据源测试连接超时 | 容器内访问宿主机请用 `host.docker.internal` 而不是 `127.0.0.1` |
| `Unknown column ...` 报错 | schema 没初始化或版本不一致；`drop database rudder; create database rudder;` 重启即可（开发环境） |
