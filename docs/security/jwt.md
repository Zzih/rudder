# JWT 与登录会话

> Rudder 用 **HMAC-SHA256 签名的无状态 JWT** 承载登录态。本章覆盖签发、校验、过期、UserContext、登录限流。配置项见 [配置参考](../configuration.md#一密钥必填)。

## 签发流程

`AuthService.generateToken(user)`：

```java
Jwts.builder()
    .claim("userId", user.getId())
    .claim("username", user.getUsername())
    .claim("isSuperAdmin", user.getIsSuperAdmin())
    .issuedAt(now)
    .expiration(now + jwtExpiration)
    .signWith(jwtKey)         // HMAC-SHA256(RUDDER_JWT_SECRET)
    .compact()
```

签发入口：

| 入口 | 触发 |
|:---|:---|
| `POST /api/auth/login` | 本地账号（用户名 / 密码） |
| `POST /api/auth/sso/ldap` | LDAP / AD（详见 [sso.md](sso.md)） |
| `GET /api/auth/sso/callback?provider=oidc&code=...` | OIDC 回调（详见 [sso.md](sso.md)） |

三种入口都最终走 `AuthService.generateToken(user)`，区别仅在用户从哪来 / 是否带 `sso_provider`。

### Claims

```json
{
  "userId": 12,
  "username": "alice",
  "isSuperAdmin": false,
  "iat": 1735200000,
  "exp": 1735243200
}
```

> 不放 workspace 角色 / 列表 — 因为一个用户可同时属于多个 workspace，每个 workspace 的角色独立；workspace role 由 `PermissionInterceptor` 在请求路径上识别 `workspaceId` 后**实时**查 `t_r_workspace_member`。

## 密钥与算法

```yaml
rudder:
  security:
    jwt-secret:     ${RUDDER_JWT_SECRET}    # ≥ 32 字节
    jwt-expiration: 43200000                # 12 小时（写死在 yml，env 不可覆盖）
```

`Keys.hmacShaKeyFor(secret.getBytes())`：

- HS256 算法；HS512 / HS384 由 secret 长度自动选择
- secret 长度由 `SecurityConfigValidator` 校验，启动时 < 32 直接拒绝
- API Server 与 Execution **共用同一个 `jwt-secret`**（Execution 端虽不直接处理用户登录，但需要校验 Server 透传的内部 JWT，详见下文）

## 校验

`PermissionInterceptor`（HandlerInterceptor）流程：

```
1. 从 Authorization header 取 "Bearer <token>"
2. AuthService.parseToken(token)
   └─ Jwts.parser().verifyWith(jwtKey).parseSignedClaims(...)
3. 异常分支：
   - ExpiredJwtException   → BizException(TOKEN_EXPIRED, 401)
   - 其它解析失败          → AuthException("Invalid token", 401)
4. 成功 → 把 userId / username / isSuperAdmin 塞到 UserContext（ThreadLocal）
5. 检查 @RequireRole（详见 permissions.md）
```

### `UserContext`

```java
public class UserContext {
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();
    public static UserInfo get() { return HOLDER.get(); }

    @Data public static class UserInfo {
        Long userId;
        String username;
        String role;       // 当前 workspace 的角色 / SUPER_ADMIN（拦截器二次填充）
    }
}
```

业务代码用 `UserContext.get()` 拿当前用户，**不要从 controller 参数往下手动透传**。`@Async` 任务跨线程时由 `Spring TaskDecorator` 复制 ThreadLocal（已配在 `AsyncConfig`）。

## 过期与续期

- 默认 12h 过期（`43200000ms`）
- **没有 refresh token**：过期后前端拿到 401 直接跳登录页
- 修改过期时间需要**改 `application.yml`** 后重打包，不通过 env 覆盖（避免运行期偏差）
- 缩短过期时间可降低 token 泄漏窗口；过短会频繁打扰用户

## 强制登出

JWT 是**无状态**的——服务端不维护 token 黑名单。强制下线手段：

| 场景 | 手段 |
|:---|:---|
| 单用户下线 | 把用户密码 reset 一次（不影响其它用户）；如果是 SSO，删 `sso_id` 后下次回调要重新创建关联 |
| 全员强制登出 | 轮换 `RUDDER_JWT_SECRET`（详见 [rotation.md](rotation.md)） |
| 紧急用户禁用 | 删用户 / 暂未提供"disabled"标记字段 — 业务上靠 `t_r_workspace_member` 移除 + reset 密码组合 |

## RPC 内部 token

Server↔Execution 的 RPC **不走** JWT，而是 HMAC-SHA256 签名（`RUDDER_RPC_AUTH_SECRET`）。详见 [RPC 协议](../rpc.md)。Execution 在执行用户任务时如果需要回调 Server 的某些接口（少见），由 Server 在 dispatch 时透传一个内部 short-lived JWT，签发同一个 `RUDDER_JWT_SECRET`。这就是为什么两端必须共享 secret。

## 登录限流

`AuthController.enforceLoginRateLimit`：

```
key      = "login:" + clientIp
limit    = 10 次 / 60 秒
exceeded → TooManyRequestsException → 429
```

实现走 `RateLimitService`（Redis token bucket）。**LDAP** 共用同一条限流；**OIDC** 因走 IdP 重定向不计数（IdP 侧负责）。

绕过条件：来自不同 IP 的请求独立计数；`X-Forwarded-For` 由 `HttpUtils.resolveClientIp` 解析，反代要正确传递。

## CSRF / XSRF

- API 全部 `Authorization: Bearer <token>`，**不依赖 cookie**
- 前端把 JWT 存 `localStorage`，每个请求由 axios 拦截器加 header
- 不需要 CSRF token（无 cookie 就无 CSRF 攻击面）

XSS：

- 前端 Vue 模板默认 escape，所有用户输入走 `DOMPurify`（已引入 `dompurify`）
- 富文本内容（脚本编辑器、AI 输出）经 `RedactionAdvisor` + DOMPurify 双层处理

## CORS

默认仅允许同源；前端开发模式 `5173` 通过 vite proxy 转发到 `5680`。生产建议反代统一 host / port，避免开 CORS。

需要开放跨域的话，在 `application.yml` 加 Spring `WebMvcConfigurer`，或在反代层加。

## SSE / WebSocket

AI turn 走 SSE：`Authorization` header 在初始 EventSource 建连时设置，连接保持期间 token 不再续；如果连接活过 token 过期时间（>12h），会被反代或服务端 timeout 关。前端会断线自动重连，重连时若 token 已过期跳登录页。

## 排障

| 症状 | 排查 |
|:---|:---|
| 401 `Token expired` | 重新登录；如果反复出现可能时钟漂移（NTP） |
| 401 `Invalid token` | secret 漂移 / 节点间 secret 不一致 / token 被截断 |
| 登录成功但所有接口 401 | 反代 `Authorization` header 未透传（Nginx `proxy_set_header`） |
| 429 Too many login | 同 IP 1 分钟超过 10 次；要么等 1 分钟，要么 IP 在反代被 NAT |
| 强制下线生效缓慢 | JWT 无状态，最多等 `jwt-expiration` 时间 / 或全员轮换 secret |
| Execution 报 `Token expired` 处理 Server 透传 | 双端时钟差距过大；NTP 同步 |

## 监控

| Metric | 含义 |
|:---|:---|
| `rudder_auth_login_total{result}` | 登录次数（success / failed / rate_limited） |
| `rudder_auth_token_parse_total{result}` | Token 校验（valid / expired / invalid） |
| `rudder_auth_active_users` | 最近 5 分钟有过有效请求的 user 数（去重） |

异常告警建议：`failed > 50 / min`、`rate_limited > 10 / min`（同源刷登录）。

## 相关文档

- [SSO（OIDC + LDAP）](sso.md)
- [审计日志](audit.md)
- [密钥轮换](rotation.md)
- [权限模型](../permissions.md)
- [配置参考](../configuration.md#一密钥必填)
