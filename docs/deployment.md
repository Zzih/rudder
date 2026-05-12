# 部署指南

> 涵盖 tarball / Docker / 多副本三种姿势，以及监控、升级、回滚。所有可调参数见 [配置参考](configuration.md)。

## 部署形态

```
 ┌───────────────┐           ┌───────────────┐
 │  Server #1    │           │  Server #2    │     ← rudder-api，HTTP 5680 / RPC 5690
 │ (api-server)  │           │ (api-server)  │
 └──────┬────────┘           └──────┬────────┘
        │       Netty RPC（auth-secret）        │
        ↓                                       ↓
 ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
 │ Execution #1  │  │ Execution #2  │  │ Execution #3  │  ← rudder-execution，HTTP 5681 / RPC 5691
 └───────────────┘  └───────────────┘  └───────────────┘
        ↓
 MySQL（持久化 + 服务注册 t_r_service_registry）
 Redis（流取消 / 限流 / 缓存）
```

- Server 与 Execution **可独立水平扩**；Server 走 Nginx / SLB 做前端反代
- 服务注册表 `t_r_service_registry` 保存 (host, http_port, rpc_port, type, last_heartbeat_at)
- 心跳 10s 一次，`heartbeat-timeout=30` 秒未更新自动 `OFFLINE`

## 一、Tarball 部署（推荐生产）

### 打包

```bash
./mvnw clean package -DskipTests
# 产物：rudder-dist/target/rudder-<version>-SNAPSHOT.tar.gz
```

### 解压并配置

```bash
mkdir -p /opt/rudder && cd /opt/rudder
tar -xzf rudder-<version>-SNAPSHOT.tar.gz
cd rudder-<version>-SNAPSHOT

# 在解压后的根目录放 .env（被 bin/env.sh 自动加载）
cat > .env <<'EOF'
RUDDER_JWT_SECRET=<32+ chars>
RUDDER_ENCRYPT_KEY=<32+ chars>
RUDDER_RPC_AUTH_SECRET=<32+ chars>
RUDDER_DB_URL=jdbc:mysql://prod-mysql:3306/rudder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true
RUDDER_DB_USERNAME=rudder
RUDDER_DB_PASSWORD=<strong-password>
RUDDER_REDIS_HOST=prod-redis
RUDDER_REDIS_PASSWORD=<redis-password>
RUDDER_NODE_ID=server-a-01
RUDDER_LOG_LEVEL=INFO
API_SERVER_HEAP=-Xms2g -Xmx4g
EXECUTION_SERVER_HEAP=-Xms1g -Xmx2g
EOF
```

### 启动 / 停止

```bash
# 启动两个进程
bin/rudder-daemon.sh start all

# 单独启
bin/rudder-daemon.sh start api-server
bin/rudder-daemon.sh start execution-server

# 状态 / 停止 / 重启
bin/rudder-daemon.sh status all
bin/rudder-daemon.sh stop all
bin/rudder-daemon.sh restart all
```

`rudder-daemon.sh` 会用 `nohup` 后台拉起，PID 写到 `pid/<server>.pid`，标准输出到 `logs/<server>.out`。

## 二、Docker 部署

### 构建镜像

```bash
# 1. 先打 tarball，Dockerfile 依赖它
./mvnw clean package -DskipTests

# 2. 构建（在项目根目录执行，COPY 是相对路径）
docker build -f rudder-dist/src/main/docker/api-server.dockerfile       -t rudder/api:<tag> .
docker build -f rudder-dist/src/main/docker/execution-server.dockerfile -t rudder/execution:<tag> .
```

镜像基于 `eclipse-temurin:21-jre`，入口脚本进入容器自动前台运行（`PID == 1` → 触发 `exec` 路径）。

### docker-compose 示例

```yaml
version: "3.9"
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: rudder123
      MYSQL_DATABASE: rudder
      MYSQL_USER: rudder
      MYSQL_PASSWORD: rudder123
    volumes: [ "mysql-data:/var/lib/mysql" ]
    ports: [ "3306:3306" ]

  redis:
    image: redis:7
    command: ["redis-server", "--requirepass", "redispwd"]
    ports: [ "6379:6379" ]

  rudder-api:
    image: rudder/api:dev
    depends_on: [ mysql, redis ]
    environment:
      RUDDER_JWT_SECRET: ${RUDDER_JWT_SECRET}
      RUDDER_ENCRYPT_KEY: ${RUDDER_ENCRYPT_KEY}
      RUDDER_RPC_AUTH_SECRET: ${RUDDER_RPC_AUTH_SECRET}
      RUDDER_DB_URL: jdbc:mysql://mysql:3306/rudder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true
      RUDDER_DB_USERNAME: rudder
      RUDDER_DB_PASSWORD: rudder123
      RUDDER_REDIS_HOST: redis
      RUDDER_REDIS_PASSWORD: redispwd
    ports: [ "5680:5680", "5690:5690" ]

  rudder-execution:
    image: rudder/execution:dev
    depends_on: [ mysql, redis, rudder-api ]
    environment:
      RUDDER_JWT_SECRET: ${RUDDER_JWT_SECRET}
      RUDDER_ENCRYPT_KEY: ${RUDDER_ENCRYPT_KEY}
      RUDDER_RPC_AUTH_SECRET: ${RUDDER_RPC_AUTH_SECRET}
      RUDDER_DB_URL: jdbc:mysql://mysql:3306/rudder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true
      RUDDER_DB_USERNAME: rudder
      RUDDER_DB_PASSWORD: rudder123
      RUDDER_REDIS_HOST: redis
      RUDDER_REDIS_PASSWORD: redispwd
    ports: [ "5681:5681", "5691:5691" ]

volumes:
  mysql-data: {}
```

### 注意

- 容器之间走 service name 互访（如 `mysql`、`redis`）
- Server↔Execution 跨容器走 RPC，要保证 `RUDDER_API_RPC_PORT` / `RUDDER_EXECUTION_RPC_PORT` 在容器网络可达
- 任务工作目录 `RUDDER_EXECUTION_WORK_DIR` 需要挂载持久卷或挂宿主机目录（默认 `/tmp/rudder/tasks`，容器重启丢失）

## 三、多副本水平扩展

### 多 Server

- 反代（Nginx / Traefik / SLB）打到任意 Server 实例
- WebSocket / SSE 路径需开启长连接：`proxy_read_timeout` 拉到 600s+，禁用 buffering
- 每个 Server 注册一条 `t_r_service_registry`，工作流调度由内部分布式锁保证不重复触发

#### 反代示例（Nginx）

```nginx
upstream rudder_api {
  server 10.0.0.11:5680;
  server 10.0.0.12:5680;
}

server {
  listen 443 ssl http2;
  server_name rudder.example.com;

  location / {
    proxy_pass http://rudder_api;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;

    proxy_buffering off;        # SSE：流式输出别 buffer
    proxy_read_timeout 600s;
  }
}
```

### 多 Execution

- Server 派发任务时按算法（轮询 / 负载 / workspace 亲和）选 ONLINE Execution
- 加 / 减 Execution 不需要重启 Server，注册到 `t_r_service_registry` 即可被发现
- 同机部署多 Execution 时务必区分 `RUDDER_EXECUTION_PORT` 与 `RUDDER_EXECUTION_RPC_PORT`

### RPC `auth-secret`

集群内所有节点必须使用同一个 `RUDDER_RPC_AUTH_SECRET`，密钥变更要做**双密钥滚动**，否则会出现握手失败的孤儿节点。

## 四、监控

### Prometheus 抓取

```yaml
scrape_configs:
  - job_name: rudder-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [ "10.0.0.11:5680", "10.0.0.12:5680" ]
  - job_name: rudder-execution
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [ "10.0.0.21:5681", "10.0.0.22:5681" ]
```

关键 metric 命名空间：

| 前缀 | 含义 |
|:---|:---|
| `rudder_workflow_*` | 工作流调度 / 执行 |
| `rudder_task_*` | 任务执行（按 TaskType 打 tag） |
| `rudder_rpc_*` | RPC 请求 / 错误 / 延迟 |
| `rudder_ai_*` | AI turn / token / tool / cancel |
| `jvm_*` / `process_*` / `system_*` | Spring Boot Actuator 标配 |

### 健康检查

```
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

readiness 失败时反代应剔除节点；liveness 失败时容器编排（K8s）应重启。

### 日志

| 文件 | 内容 |
|:---|:---|
| `logs/api-server.out` / `logs/execution-server.out` | nohup 标准输出（启动 banner + 异常栈） |
| `logs/rudder-api.log` / `logs/rudder-execution.log` | logback 滚动日志（INFO 及以上） |
| `logs/tasks/<workflow>/<taskInstanceId>.log` | 每个任务实例独立日志，TTL 由清理策略决定 |

日志已经过 `%msg` converter 脱敏（命中 `t_r_redaction_rule` 的字段会被替换）。

## 五、升级

### 滚动升级（推荐）

1. 数据库 schema 兼容性自检：对比 `rudder-dao/src/main/resources/sql/schema.sql` diff，DBA 评估是否需要离线迁移
2. 备份 MySQL（至少 dump `t_r_*` 全表）
3. 一台台 Server 走「停 → 替换 tarball → 启」；流量自动到其他副本
4. Execution 同样滚动；正在执行的任务会被标 `INTERRUPTED`，由 Server 端 `WorkflowInstanceRunner` 决定是否补救
5. 完成后比对 `actuator/info` 确认版本

### 大版本（破坏性 schema 变更）

1. 进入维护窗口
2. 全量停 Server / Execution
3. 跑迁移脚本
4. 启动新版 Server，确认 `t_r_service_registry` 干净，旧节点过期被 `OFFLINE`
5. 启动 Execution

## 六、回滚

- Tarball：保留上一版 `tar.gz`，原地替换重启
- Docker：`docker tag` 保留 `previous`，`docker compose up -d` 切回
- Schema：如有破坏性变更需要 DBA 提前准备 `rollback.sql`

## 七、生产前 checklist

- [ ] 三个密钥全部换成强随机串（≥ 32 字节）
- [ ] `admin / admin123` 默认账号已禁用或改密
- [ ] `RUDDER_SQL_INIT_MODE=never`，schema/data 由 DBA 受控初始化
- [ ] `RUDDER_LOG_LEVEL=INFO`
- [ ] `RUDDER_NODE_ID` 显式赋值，便于多副本日志区分
- [ ] `JAVA_TOOL_OPTIONS` 或 `RUDDER_JVM_OPTS` 配置 `-XX:+ExitOnOutOfMemoryError` + heap dump 路径
- [ ] Prometheus 抓取 + 告警：rpc 错误率、workflow 失败率、Execution 在线数
- [ ] MySQL / Redis 主从 + 备份策略
- [ ] SSO / LDAP 接入
- [ ] 反代 SSL + WebSocket / SSE 长连接配置
- [ ] 防火墙：HTTP（5680）开放给用户，RPC（5690 / 5691）只对内
