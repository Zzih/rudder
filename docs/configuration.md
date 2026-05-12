# 配置参考

> 涵盖 `.env` / `application.yml` / 启动脚本三层。SPI provider（AI / 元数据 / 文件 / 审批 / 通知 / 版本）的配置不在这里 —— 它们由 **管理后台 UI** 写入 `t_r_*_config` 表，运行时热生效。

## 配置优先级

```
JVM -D 系统属性  >  环境变量 / .env  >  application.yml 默认值
```

`.env` 在启动脚本 `bin/env.sh` 里通过 `set -a; . .env; set +a` 注入为环境变量。

## 一、密钥（必填）

| 变量 | 用途 | 长度要求 | 默认 |
|:---|:---|:---|:---|
| `RUDDER_JWT_SECRET` | JWT 签发 / 校验 | ≥ 32 字节 | 无（必填） |
| `RUDDER_ENCRYPT_KEY` | 数据源凭证 / 第三方 token AES 加密 | ≥ 32 字节 | 无（必填） |
| `RUDDER_RPC_AUTH_SECRET` | Server↔Execution RPC 鉴权 | ≥ 32 字节 | 无（必填，两端必须一致） |

> 缺失或长度不足时启动直接失败。轮换密钥流程见 [security/rotation.md](security/rotation.md)。

## 二、数据库与 Redis

| 变量 | 默认 | 备注 |
|:---|:---|:---|
| `RUDDER_DB_URL` | `jdbc:mysql://127.0.0.1:3306/rudder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true` | `allowMultiQueries=true` 必带 |
| `RUDDER_DB_USERNAME` | `rudder` | |
| `RUDDER_DB_PASSWORD` | `rudder123` | 生产换成强密码 |
| `RUDDER_REDIS_HOST` | `127.0.0.1` | |
| `RUDDER_REDIS_PORT` | `6379` | |
| `RUDDER_REDIS_PASSWORD` | 空 | 有密码时填 |
| `RUDDER_REDIS_DB` | `0` | |

HikariCP 连接池：`minimum-idle=5 / maximum-pool-size=20 / connection-timeout=60000`（写死在 yml，可在打包前改 `application.yml`）。

Redis Lettuce 连接池：`max-active=16 / max-idle=8 / min-idle=2`，timeout `3s`（API Server 端）。

### 数据库初始化

`spring.sql.init.mode=always`（受 `RUDDER_SQL_INIT_MODE` 控制），加载顺序：

```
classpath:sql/schema.sql       表结构（43 张 t_r_*）
classpath:sql/data.sql         最小种子（admin 用户 + SPI 默认）
```

生产部署：

- 改 `RUDDER_SQL_INIT_MODE=never`，由 DBA 一次性手工跑 `schema.sql + data.sql`

`continue-on-error: true` —— 重复启动时已有数据不会让进程崩，但也意味着 schema 漂移要靠人审。

## 三、端口与 RPC

### Server (`rudder-api`)

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_API_PORT` | `5680` | HTTP / WebSocket / SSE |
| `RUDDER_API_RPC_PORT` | `5690` | Netty RPC（接 Execution 反向回调 / 任务状态上报） |

### Execution (`rudder-execution`)

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_EXECUTION_PORT` | `5681` | HTTP（健康检查 / 内部接口） |
| `RUDDER_EXECUTION_RPC_PORT` | `5691` | Netty RPC（接受 Server 派发任务） |

### RPC 鉴权

`rudder.rpc.auth-secret` 双端必须一致；握手时不通过则连接被拒、Execution 永远注册不上来。

## 四、节点身份

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_NODE_ID` | 空（自动 `hostname-pid`） | 写入日志 / Prometheus 指标 tag，用于多副本排障 |

## 五、JVM 与启动脚本

`bin/env.sh` 暴露的可调项：

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `JAVA_HOME` | 自动探测 Java 21 | 没探到要显式设置 |
| `API_SERVER_HEAP` | `-Xms512m -Xmx1024m` | API Server 堆 |
| `EXECUTION_SERVER_HEAP` | `-Xms256m -Xmx512m` | Execution 堆 |
| `RUDDER_JVM_OPTS` | `-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError` | 通用 JVM 参数 |
| `RUDDER_LOG_DIR` | `$RUDDER_HOME/logs` | 日志目录 |
| `RUDDER_PID_DIR` | `$RUDDER_HOME/pid` | PID 文件目录 |
| `RUDDER_FOREGROUND` | 未设 | `=true` 前台运行（Docker 入口默认前台） |

## 六、异步线程池（审计 / @Async）

```
spring.task.execution.pool.core-size       RUDDER_ASYNC_CORE_SIZE     (4)
spring.task.execution.pool.max-size        RUDDER_ASYNC_MAX_SIZE      (16)
spring.task.execution.pool.queue-capacity  RUDDER_ASYNC_QUEUE_CAPACITY (1000)
```

> 审计日志走 `@Async`，`queue-capacity` 不设上限会让 DB 抖动时积压到 OOM。高流量调大 `MAX_SIZE` 与 `QUEUE_CAPACITY`，低规格保持小。

## 七、工作流执行器

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_WORKFLOW_EXECUTOR_THREADS` | `100` | Server 端 WorkflowInstanceRunner 并发线程数 |

## 八、Execution 工作目录

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_EXECUTION_WORK_DIR` | `/tmp/rudder/tasks` | 任务运行临时目录（脚本落盘 / 中间文件） |
| `RUDDER_EXECUTION_CLEAN_WORK_DIR` | `true` | 任务结束后清理工作目录 |

## 九、文件存储

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_FILE_LOCAL_DIR` | `./local/rudder-data` | LOCAL provider 落地目录 |

> HDFS / OSS / S3 provider 的 access key / endpoint 等通过管理后台 UI 配置，不走 env。

## 十、SSO

### OIDC（Okta / Azure AD / Keycloak / Auth0）

| 变量 | 默认 |
|:---|:---|
| `RUDDER_SSO_OIDC_ENABLED` | `true` |
| `RUDDER_SSO_OIDC_CLIENT_ID` | 空 |
| `RUDDER_SSO_OIDC_CLIENT_SECRET` | 空 |
| `RUDDER_SSO_OIDC_REDIRECT_URI` | `http://localhost:5680/api/auth/sso/callback?provider=oidc` |
| `RUDDER_SSO_OIDC_ISSUER` | 空 |
| `RUDDER_SSO_OIDC_AUTHORIZATION_URI` | 空 |
| `RUDDER_SSO_OIDC_TOKEN_URI` | 空 |
| `RUDDER_SSO_OIDC_USER_INFO_URI` | 空 |
| `RUDDER_SSO_OIDC_SCOPES` | `openid profile email` |
| `RUDDER_SSO_FRONTEND_REDIRECT_URL` | `http://localhost:5173/sso/login` |

### LDAP / Active Directory

| 变量 | 默认 |
|:---|:---|
| `RUDDER_SSO_LDAP_ENABLED` | `false` |
| `RUDDER_SSO_LDAP_URL` | `ldap://localhost:389` |
| `RUDDER_SSO_LDAP_TRUST_ALL_CERTS` | `false` |
| `RUDDER_SSO_LDAP_BASE_DN` | `dc=company,dc=com` |
| `RUDDER_SSO_LDAP_BIND_DN` | 空 |
| `RUDDER_SSO_LDAP_BIND_PASSWORD` | 空 |
| `RUDDER_SSO_LDAP_USER_SEARCH_FILTER` | `(&(objectClass=user)(sAMAccountName={0}))` |
| `RUDDER_SSO_LDAP_USERNAME_ATTR` | `sAMAccountName` |
| `RUDDER_SSO_LDAP_EMAIL_ATTR` | `mail` |
| `RUDDER_SSO_LDAP_DISPLAY_NAME_ATTR` | `displayName` |

JWT 过期时间硬编码 `12h`（`43200000` ms），改需要直接改 `application.yml`。

## 十一、JWT / Session

| 字段（仅 yml） | 默认 | 说明 |
|:---|:---|:---|
| `rudder.security.jwt-expiration` | `43200000`（12 小时） | API / Execution 两端必须一致 |

## 十二、DolphinScheduler 对接

| 变量 | 默认 |
|:---|:---|
| `RUDDER_DOLPHIN_CLIENT_URL` | `http://127.0.0.1:12348` |
| `RUDDER_DOLPHIN_CLIENT_TOKEN` | 空 |

> 集成方式与发布流程见 [dolphinscheduler.md](dolphinscheduler.md)。

## 十三、Actuator / Prometheus

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_ACTUATOR_ENDPOINTS` | `health,info,prometheus` | 暴露端点列表 |

- `GET /actuator/health` — 健康（含 readiness/liveness 探针）
- `GET /actuator/prometheus` — Prometheus 抓取
- `GET /actuator/info` — 版本

> 生产环境建议把 `management.server.port` 拆开监听，或用 IP 白名单控制 `/actuator/**`，避免 Prometheus 端点裸奔。

## 十四、日志

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `RUDDER_LOG_LEVEL` | `DEBUG` | `io.github.zzih.rudder` 包级别 |

`logback-spring.xml` 输出到 `RUDDER_LOG_DIR`，并接入 `%msg` converter 做日志脱敏（脱敏规则由 `t_r_redaction_rule` 驱动）。

## 十五、SPI provider 配置（**不在这里**）

| 类别 | 在哪里配 | 落表 |
|:---|:---|:---|
| AI（LLM / Embedding / Vector） | `管理 → AI 配置` | `t_r_ai_config` |
| 元数据（DataHub / OpenMetadata / JDBC） | `管理 → 元数据` | `t_r_metadata_config` |
| 文件存储（HDFS / OSS / S3 / Local） | `管理 → 文件存储` | `t_r_file_config` |
| 审批（飞书 / Slack / KissFlow） | `管理 → 审批渠道` | `t_r_approval_config` |
| 通知（飞书 / 钉钉 / Slack） | `管理 → 通知渠道` | `t_r_notification_config` |
| 版本（MySQL / Git） | `管理 → 版本存储` | `t_r_version_config` |
| Runtime（local / aliyun / aws） | `管理 → Runtime` | `t_r_runtime_config` |
| 结果格式 | `管理 → 结果格式` | `t_r_result_config` |

> 历史 env 变量 `RUDDER_AI_*` / `RUDDER_VERSION_STORE` / `RUDDER_GIT_*` 已废弃，会被忽略。

## 校验

启动时校验顺序：

1. `RUDDER_JWT_SECRET / ENCRYPT_KEY / RPC_AUTH_SECRET` 长度 < 32 → 失败退出
2. MySQL / Redis 不通 → Spring 启动失败（无 fallback）
3. RPC 端口被占 → Netty bind 失败
4. SPI plugin 注解扫描失败（如 `META-INF/services` 缺）→ ArchUnit 测试可在 `mvn test` 提前暴露
