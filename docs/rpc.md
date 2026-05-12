# RPC 协议

> Rudder 自研的 Netty 长连接 RPC，承载 Server↔Execution 之间的任务派发、状态回调、日志查询、结果拉取。本章覆盖协议格式、鉴权、服务契约、Spring 装配、扩展。

## 为什么自研

候选：Dubbo / gRPC / Spring Cloud。最终选择自己写一套，理由：

- **轻量** — Server↔Execution 两类节点，调用面有限（< 10 个方法），不需要服务发现、负载均衡、链路追踪等大件
- **零依赖中间件** — 不引入 ZooKeeper / Nacos / Consul，减少运维负担
- **简单鉴权** — HMAC-SHA256 + 时间戳防重放就够了，不需要 mTLS / JWT
- **JSON body** — 调试 / 抓包直接可读，比 protobuf 更便利

代价：单进程内的契约管理需要约定（`@RpcService` / `@RpcMethod` 注解），跨语言难。但 Rudder 后端全 Java，目前不是问题。

## 模块结构

```
rudder-rpc/
└── io.github.zzih.rudder.rpc/
    ├── annotation/    @RpcService / @RpcMethod
    ├── protocol/      RpcMessage / RpcHeader / RpcRequest / RpcResponse / Encoder / Decoder / RpcAuth
    ├── server/        RpcServer / RpcServerHandler / MethodRegistry / MethodInvoker
    ├── client/        RpcClient / RpcClientHandler / RpcInvocationHandler / RpcFuture / Clients
    ├── service/       I*Service 契约（接口 + @RpcService）
    └── spring/        RpcAutoConfiguration + RpcProperties
```

## 协议格式

```
+-------+--------+------------+----------+---------+--------+
| MAGIC | VER    | HEADER_LEN | BODY_LEN | HEADER  | BODY   |
| 2B    | 1B     | 4B         | 4B       | n bytes | m bytes|
+-------+--------+------------+----------+---------+--------+

MAGIC = 0x5244     ("RD" 字面)
VER   = 1
HEADER = JSON(RpcHeader)
BODY   = JSON(RpcRequest 或 RpcResponse)
```

### `RpcHeader`

```java
{
    "methodId":  "io.github.zzih.rudder.rpc.service.ITaskExecutionService#dispatch",
    "opaque":    1234567,           // 请求 ID，请求 / 响应配对
    "type":      0,                 // 0=请求 1=响应
    "timestamp": 1735200000000,     // 毫秒时间戳
    "signature": "<HMAC-SHA256 hex>"
}
```

- `methodId` = `<接口全限定名>#<方法名>`，server 端用它找到注册的 invoker
- `opaque` = 全局自增；client 用它把 response 路由到对应的 `RpcFuture`
- `signature` = HMAC-SHA256(`secret`, `methodId|opaque|timestamp`)，hex 编码

### `RpcRequest` / `RpcResponse`

```java
class RpcRequest  { Object[] args; String[] argTypes; }
class RpcResponse { Object result; String errorMessage; String errorClass; }
```

参数走 Jackson 序列化；返回值同理。`argTypes` 让 server 端在反序列化时不依赖泛型擦除后的类型信息。

## 鉴权（防重放 + 防篡改）

`RpcAuth.sign` / `RpcAuth.verify`：

```
sign     = HMAC-SHA256(secret, methodId + "|" + opaque + "|" + timestamp)
timeSkew = |serverTime - timestamp|
拒绝条件 = signature 缺失 / signature 不匹配 / timeSkew > 5min
```

- `secret` 来自 `RUDDER_RPC_AUTH_SECRET`，**Server / Execution 必须一致**且 ≥ 32 字节
- 时间窗 5 分钟（`RpcAuth.MAX_SKEW_MS`）；时钟同步差距过大会全部拒绝，部署时 NTP 必备
- `MessageDigest.isEqual()` 恒定时间比较，避免计时攻击
- Header / body 不加密——HMAC 只防篡改不防偷窥；如果在跨域 / 跨 VPC 部署，建议在 IaaS 侧用 IPSec / WireGuard 隧道

## 服务契约

### 命名

- 接口标 `@RpcService`，放在 `rudder-rpc/service/I<Name>Service.java` 或业务模块
- 方法标 `@RpcMethod(timeout = -1)`（`-1` 用全局默认超时）

### 内置契约

```java
@RpcService
interface ITaskExecutionService {           // Execution 实现，Server 调用
    @RpcMethod void   dispatch(TaskDispatchRequest req);
    @RpcMethod void   cancel(Long taskInstanceId);
    @RpcMethod String triggerSavepoint(Long taskInstanceId);
}

@RpcService
interface ITaskCallbackService {            // Server 实现，Execution 调用
    @RpcMethod void onTaskStarted(...);
    @RpcMethod void onTaskFinished(...);
    @RpcMethod void onTaskFailed(...);
}

@RpcService
interface ILogService {                     // Execution 实现
    @RpcMethod LogChunk read(Long taskInstanceId, long offset, int limit);
}

@RpcService
interface IResultService {                  // Execution 实现
    @RpcMethod ResultPage page(Long taskInstanceId, int pageNo, int pageSize);
}
```

> 契约的 DTO 类必须放在 `rudder-common` 或 `rudder-rpc`，让两端都能依赖。

## Server 端

### 装配

`RpcAutoConfiguration` 自动：

1. 创建 `RpcServer(port, ioThreads, workerThreads, authSecret)` Bean
2. `@PostConstruct`：扫所有 `@Component` Bean，凡是实现了带 `@RpcService` 注解的接口的，注册到 `MethodRegistry`
3. `start()` 起 Netty `ServerBootstrap`，绑定 `rudder.rpc.port`（API 进程取 `RUDDER_API_RPC_PORT`，Execution 进程取 `RUDDER_EXECUTION_RPC_PORT`）

### 调用流程

```
Client ─→ Decoder
       → RpcServerHandler.channelRead
       → 校验 magic / version
       → RpcAuth.verify(header)
       → MethodRegistry.find(methodId) → MethodInvoker
       → 反射调用业务 bean.method(args)
       → 包装结果 / 异常为 RpcResponse
       → Encoder ─→ Client
```

异常会被序列化 `errorClass + errorMessage`；client 侧 throw 重新抛出。

## Client 端

### 取代理

```java
ITaskExecutionService stub = Clients.proxy(
    ITaskExecutionService.class,
    "execution-host",
    5691
);

stub.dispatch(request);   // 同步阻塞调用
```

`Clients.proxy` 内部：

1. `RpcClient.getOrCreateChannel(host, port)` —— Lazy 建 Netty channel，连接池复用
2. 创建 JDK Proxy，所有方法调用走 `RpcInvocationHandler`
3. `RpcInvocationHandler.invoke` —— 构造 `RpcMessage`，签名，写入 channel
4. `RpcFuture` 阻塞等响应（按方法或全局 `RpcProperties.requestTimeout`）

### 连接管理

- 每个 (host, port) 维护一条长连接
- TCP 断开 → 自动重连
- 业务级超时（`RpcFuture.get(timeout)`）独立于 TCP 层
- Server 关停时下发关闭 → client 收到 `channelInactive` 标记 channel 为 stale，下次调用重连

## Spring 配置

```yaml
rudder:
  rpc:
    port: ${RUDDER_API_RPC_PORT:5690}        # Execution 进程对应 ${RUDDER_EXECUTION_RPC_PORT:5691}
    auth-secret: ${RUDDER_RPC_AUTH_SECRET:}    # 必填，≥ 32 字节
```

`RpcProperties` 还接受（默认值由代码决定）：

- `io-threads` — Netty boss/io 线程数
- `worker-threads` — 业务线程池大小
- `connect-timeout` — 建连超时
- `request-timeout` — 全局请求超时

未配 `auth-secret` 启动直接失败。

## 派发流程（端到端）

```
Server 端 ──────────────────────────────────────────────
1. WorkflowInstanceRunner 取就绪节点 → TaskInstanceFactory 落库
2. TaskDispatchService 选 ONLINE Execution（轮询 / 负载策略）
3. Clients.proxy(ITaskExecutionService.class, host, port).dispatch(req)
4. 等 RPC 返回 ack（同步）
5. 后续状态由 Execution 主动 callback

Execution 端 ──────────────────────────────────────────
1. RpcServerHandler 收到 dispatch RPC
2. TaskExecutionServiceImpl.dispatch 入队，立即返回
3. 异步线程：TaskPipeline 拉资源 → 执行 → 落本地日志 / 结果
4. 关键节点 callback 回 Server：
   - Clients.proxy(ITaskCallbackService.class, serverHost, 5690).onTaskFinished(...)
```

## 多 Execution 路由

Server 调 Execution 时按 `t_r_service_registry` 选实例：

```sql
SELECT host, http_port, rpc_port FROM t_r_service_registry
WHERE type = 'EXECUTION' AND status = 'ONLINE'
ORDER BY last_heartbeat_at DESC
```

具体策略（轮询 / 最少负载 / workspace 亲和）由调度层 `TaskDispatchService` 决定，RPC 层只负责通信。

## 排障

| 症状 | 原因 |
|:---|:---|
| 启动报 `RUDDER_RPC_AUTH_SECRET must be ≥ 32 bytes` | env 没配或长度不足 |
| Execution 注册成功但 dispatch 失败 | 双端 `auth-secret` 不一致 |
| `signature mismatch / timeskew` | 节点时钟差距 > 5 分钟，配置 NTP |
| `Method not found` | client / server 接口版本不一致；DTO 字段对不上 |
| 长期阻塞 | 调用方没设 timeout 或 timeout 太长；服务方阻塞了线程 |
| `Magic mismatch` | 端口被其它程序占用，握手到非 RPC 协议；或同 host 起了别的 RPC 服务 |

## 监控

Prometheus 暴露：

| Metric | 含义 |
|:---|:---|
| `rudder_rpc_request_total{method,status}` | 请求计数（OK / ERROR / TIMEOUT） |
| `rudder_rpc_request_duration_seconds{method}` | 调用延迟 |
| `rudder_rpc_active_connections` | 当前活跃 Netty channel 数 |
| `rudder_rpc_pending_calls` | client 端等待响应的请求数 |

可结合 Grafana 看跨节点 P99 延迟、错误率、连接抖动。

## 扩展：增加新 RPC 方法

1. 在 `I<Name>Service.java` 加 `@RpcMethod` 方法
2. 实现端的 `@Component` Bean 自动被 `MethodRegistry` 收录
3. 调用端用 `Clients.proxy(...)` 拿 stub，直接调
4. 参数 / 返回值 DTO 必须 Jackson 友好（POJO + 默认构造 + getter/setter，Lombok `@Data` 即可）

不需要写代理代码，不需要改协议层。

## 替代方案对比

| 维度 | 当前自研 | gRPC | Dubbo |
|:---|:---|:---|:---|
| 协议体积 | 中（JSON） | 小（protobuf） | 中 |
| 跨语言 | 仅 JVM | ✓ | 仅 JVM |
| 服务发现 | MySQL `t_r_service_registry` | 需要 etcd / consul | 需要 ZK / Nacos |
| 鉴权 | HMAC | mTLS / JWT | mTLS / Token |
| 学习成本 | 内部 1 周 | proto 文件 / IDL | 配置体系庞大 |
| 调试 | 抓包可读 | 需要 protobuf decoder | 二进制不友好 |

如果未来有跨语言 / 多协议需求，可考虑在 RPC 层之上引入 gRPC 网关，但当前不必。

## 相关文档

- [架构总览](architecture.md) — Server / Execution 双进程模型
- [部署指南](deployment.md) — 多副本配置 + 反代
- [配置参考](configuration.md#三端口与-rpc) — RPC 端口与 auth-secret
