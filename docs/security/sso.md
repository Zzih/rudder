# SSO 集成（OIDC + LDAP）

> Rudder 支持企业 SSO 接入，登录态最终落到统一的 JWT 上。本章覆盖 OIDC（OAuth2 授权码流程）、LDAP / AD、用户合并策略、排障。

## 总览

```
                                           ┌── /api/auth/login         本地账号
浏览器 ──── /api/auth/sso/providers ───────┤── /api/auth/sso/login      OIDC 重定向到 IdP
                                           └── /api/auth/sso/ldap      LDAP 直登

OIDC: 浏览器 → IdP → /api/auth/sso/callback?provider=oidc&code=...&state=...
                              │
                              ↓
         AuthService.findOrCreateSsoUser → JWT
```

启用与否由 env 控制，详见 [配置参考 - SSO](../configuration.md#十sso)。前端调 `GET /api/auth/sso/providers` 拿当前已启用的 provider 列表，决定登录页展示哪些按钮。

## OIDC

适配通用 OAuth2 / OIDC 协议，已验证：Okta、Azure AD、Keycloak、Auth0；其它兼容 IdP 通过端点配置接入。

### 配置

```
RUDDER_SSO_OIDC_ENABLED=true
RUDDER_SSO_OIDC_CLIENT_ID=<client id>
RUDDER_SSO_OIDC_CLIENT_SECRET=<client secret>
RUDDER_SSO_OIDC_REDIRECT_URI=https://rudder.example.com/api/auth/sso/callback?provider=oidc
RUDDER_SSO_OIDC_ISSUER=https://idp.example.com
RUDDER_SSO_OIDC_AUTHORIZATION_URI=https://idp.example.com/oauth2/v1/authorize
RUDDER_SSO_OIDC_TOKEN_URI=https://idp.example.com/oauth2/v1/token
RUDDER_SSO_OIDC_USER_INFO_URI=https://idp.example.com/oauth2/v1/userinfo
RUDDER_SSO_OIDC_SCOPES=openid profile email
RUDDER_SSO_FRONTEND_REDIRECT_URL=https://rudder.example.com/sso/login
```

### 流程

```
1. 浏览器 → GET /api/auth/sso/login?provider=oidc
   └─ SsoService.buildAuthorizationUrl()
      ├─ 生成随机 state
      ├─ OneShotTokenService.put(scope="sso:state", value=state, ttl=5min)
      └─ 302 redirect 到 IdP authorize_uri?client_id=...&redirect_uri=...&state=...&scope=openid+profile+email

2. 用户在 IdP 完成认证 → IdP 302 回 redirect_uri?code=...&state=...

3. 浏览器 → GET /api/auth/sso/callback?provider=oidc&code=...&state=...
   ├─ SsoService.consumeState(state)        防 CSRF / 重放，必须命中且未消费
   ├─ POST token_uri (grant_type=authorization_code, code, ...)
   ├─ GET user_info_uri (Bearer access_token)
   ├─ AuthService.findOrCreateSsoUser("OIDC", sub, name, email, picture)
   └─ 302 → RUDDER_SSO_FRONTEND_REDIRECT_URL?token=<JWT>
```

### state 防重放

`SsoService.storeState / consumeState` 用 `OneShotTokenService`（Redis）：

- **写一次**，`consume` 时原子删
- TTL 5 分钟，超时自动清
- 失败抛 `BizException(SSO_AUTH_FAILED)`

跨节点安全：state 在 Redis，任意节点都能消费（OAuth 回调可能落到不同 Server 实例）。

### 用户字段映射

```java
sub                  → sso_id
preferred_username
  || name            → username（不重时直接用，重则 + UUID 短码）
email                → email
picture              → avatar
```

OIDC userinfo 字段语义见 [OIDC Spec § 5.1](https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims)。

### Scopes

默认 `openid profile email`：

- `openid` — 必需，标识 OIDC 流程
- `profile` — 取 `name / preferred_username / picture`
- `email` — 取 `email`

部分 IdP（如 Azure AD v2）需要额外 `offline_access`、自定义 `groups` claim 等，自行扩展 `RUDDER_SSO_OIDC_SCOPES` 即可。但 Rudder 当前**不消费**额外 claim（如 `groups`），需要把 IdP group 映射到 workspace 角色得手工实现。

### 安全注意

- **`redirect_uri` 必须 HTTPS**（本地开发例外）；IdP 端预登记白名单
- **`client_secret` 落 env** 不落代码；轮换由 IdP 侧主导
- **state 超时不放宽**：5 分钟够回调；放宽会增加重放窗口
- token 不持久化，只用一次（拿 user_info）
- 一旦发现 IdP 端账号已被合规移除，建议在 Rudder 同步删 user / 至少移出所有 workspace_member（详见用户合并）

## LDAP / Active Directory

适合企业内网域账号统一登录。Rudder 用 [UnboundID LDAP SDK](https://github.com/pingidentity/ldapsdk)（`com.unboundid.ldap.sdk.LDAPConnection`）。

### 配置

```
RUDDER_SSO_LDAP_ENABLED=true
RUDDER_SSO_LDAP_URL=ldap://ad.example.com:389       # 或 ldaps://...:636
RUDDER_SSO_LDAP_TRUST_ALL_CERTS=false               # 仅测试用
RUDDER_SSO_LDAP_BASE_DN=DC=example,DC=com
RUDDER_SSO_LDAP_BIND_DN=CN=svc-rudder,OU=Service,DC=example,DC=com
RUDDER_SSO_LDAP_BIND_PASSWORD=<service account pwd>
RUDDER_SSO_LDAP_USER_SEARCH_FILTER=(&(objectClass=user)(sAMAccountName={0}))
RUDDER_SSO_LDAP_USERNAME_ATTR=sAMAccountName
RUDDER_SSO_LDAP_EMAIL_ATTR=mail
RUDDER_SSO_LDAP_DISPLAY_NAME_ATTR=displayName
```

### 流程

```
1. 浏览器 → POST /api/auth/sso/ldap { username, password }
2. LdapService.authenticate(username, password):
   ├─ open LDAPConnection (ldaps 自动用 SSLSocketFactory)
   ├─ bind(BIND_DN, BIND_PASSWORD)         服务账号
   ├─ search(BASE_DN, USER_SEARCH_FILTER replace {0}=username)
   │    └─ 命中 → SearchResultEntry，取 DN
   ├─ bind(userDn, userPassword)           用户账号 — 验证密码
   ├─ AuthService.findOrCreateSsoUser("LDAP", userDn, ldapUsername, email, null)
   └─ JWT
```

`{0}` 占位符使用 `Filter.encodeValue(username)` 转义，防 LDAP 注入。

### 必须的服务账号

`BIND_DN` / `BIND_PASSWORD` 是只读服务账号，用于先用它 bind 拿连接、再 search 出用户 DN。**不要**用域管理员；用一个仅有 search 权限的服务账号。

如果你的 AD 允许匿名 search，把 BIND_DN / PASSWORD 留空亦可（`adminBind` 跳过）。

### LDAPS

- 推荐 `ldaps://...:636`（TLS）
- `RUDDER_SSO_LDAP_TRUST_ALL_CERTS=true` **仅测试**用，会跳过证书校验
- 生产请把 IdP 根证书装到 Java trust store（`$JAVA_HOME/lib/security/cacerts`），或挂载自定义 truststore 后通过 JVM 参数 `-Djavax.net.ssl.trustStore=...` 注入

### 字段映射

| AD / LDAP 属性 | Rudder 字段 |
|:---|:---|
| `userDn`（完整 DN） | `sso_id` |
| `sAMAccountName`（默认） | `username` |
| `mail`（默认） | `email` |
| — | `avatar`（不取，目前 LDAP 走默认空） |

域用户名 `LIU.MING@AD.EXAMPLE.COM` 不会作为 username；按 `username-attribute` 提取，更短更稳。

### 限流

`/api/auth/sso/ldap` 与 `/api/auth/login` **共用同一条登录限流**（同 key = `login:<ip>`，10 次 / 60 秒），防止暴力穷举密码。

## 用户合并策略

`AuthService.findOrCreateSsoUser(provider, ssoId, username, email, avatar)` 三步：

```
1. WHERE sso_provider=? AND sso_id=? → 命中直接返回（按 SSO 唯一标识精确匹配）
2. WHERE email=? → 命中：把现有用户绑上 SSO（写 sso_provider / sso_id），返回
3. 都未命中：自动创建新用户（password=空），username 重时加 _<UUID 短码>
```

含义：

- **同一 SSO 用户**重复登录走步骤 1，幂等
- **本地账号 → SSO 关联**：当 IdP 推过来的 email 与现有本地账号 email 一致，**自动合并**为同一用户（视邮箱为身份）
- **多 IdP 间互不干扰**：`(sso_provider, sso_id)` 唯一索引，同一邮箱在不同 provider 算两个 SSO 凭证（步骤 1 都查不到，只有步骤 2 兜底；如果步骤 2 已经把用户绑给 OIDC，再来 LDAP 走步骤 2 也会重新覆盖 sso_provider/id 字段——所以**不要把同一邮箱接到多个 IdP**，会产生绑定漂移）

> `data.sql` 默认 admin 账号没 email；OIDC / LDAP 用户登录不会被 admin 误合并。

## SSO 用户的权限

SSO 用户**首次登录**：

- `t_r_user` 自动创建，`is_super_admin = false`
- `t_r_workspace_member` 没有任何记录 → 无 workspace 可进 → 看不到 workspace 列表

需要 `SUPER_ADMIN` 或目标 workspace 的 `WORKSPACE_OWNER` 把用户加进 workspace 并指派角色。

实践建议：

- 创建一个名为 "default" 的 workspace，建账号自动加为 VIEWER（自己写个钩子或 `findOrCreateSsoUser` 后置 hook）
- 或在 admin UI 提前 import 用户列表

## 多 SSO 共存

OIDC + LDAP 可同时启用。前端 `/api/auth/sso/providers` 返回 `["OIDC", "LDAP"]`，登录页同时展示「域账号登录」+「OIDC 重定向」两个入口。

不支持多个 OIDC IdP 同时启用（`provider=oidc` 的回调只指向一份配置）。如果有多 IdP 需求：当前手段是只启用一个，剩下的部署独立环境；要支持需要扩展 `SsoProperties.oidc` 为 list。

## 测试

| 场景 | 用例 |
|:---|:---|
| OIDC happy path | 浏览器走 `/api/auth/sso/login?provider=oidc` → 完成 IdP → 落到前端登录态 |
| OIDC state 重放 | 同 state 点击两次 callback；第二次 401 |
| OIDC state 过期 | 等 5 分钟再访问 callback；401 |
| LDAP 错误密码 | 接 ratelimit；超 10 次同 IP 拒绝 |
| LDAP 用户不存在 | `LDAP_USER_NOT_FOUND` |
| LDAP TLS 证书错 | `ldaps://` + `trust-all-certs=false` 且没装根证书 → SSLHandshakeException |
| 邮箱合并 | 先建本地账号 alice@x.com，OIDC 登录 alice@x.com，应当合并为同一 user_id |

## 排障

| 症状 | 排查 |
|:---|:---|
| `/sso/providers` 返回空 | 没启用任一 provider；env 检查 |
| OIDC callback 报 `SSO_AUTH_FAILED` | state 不匹配 / 已消费 / 已过期；或 `code` 已用过；或 IdP token endpoint 返回错误 |
| OIDC callback log `error_description=...` | IdP 错误，最常见 `redirect_uri_mismatch`：实际 redirect_uri 与 IdP 注册值不一致（含 query 参数） |
| OIDC 创建用户但 `username` 是 UUID | IdP 没返回 `preferred_username` / `name`，且无 `email` 推断；调整 IdP claim |
| LDAP `LDAP_USER_NOT_FOUND` | filter 没命中；检查 `userSearchFilter` 模板与 `usernameAttribute` |
| LDAP bind 服务账号失败 | `BIND_DN` / `BIND_PASSWORD` 错；用 `ldapsearch` 命令行验证一下 |
| LDAPS connection refused | 端口 636 不通 / 防火墙；先用 `openssl s_client -connect host:636` 试 |
| 多节点 OIDC 50% 概率失败 | state 用本地内存而非 Redis（旧版本问题，当前已用 OneShotTokenService 不会出现） |

## 与现有 IAM 集成示例

### Okta

```
ISSUER:           https://<your-org>.okta.com
AUTHORIZATION:    {ISSUER}/oauth2/v1/authorize
TOKEN:            {ISSUER}/oauth2/v1/token
USER_INFO:        {ISSUER}/oauth2/v1/userinfo
SCOPES:           openid profile email
```

### Keycloak

```
ISSUER:           https://kc.example.com/realms/<realm>
AUTHORIZATION:    {ISSUER}/protocol/openid-connect/auth
TOKEN:            {ISSUER}/protocol/openid-connect/token
USER_INFO:        {ISSUER}/protocol/openid-connect/userinfo
```

### Azure AD（v2）

```
ISSUER:           https://login.microsoftonline.com/<tenant>/v2.0
AUTHORIZATION:    https://login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize
TOKEN:            https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token
USER_INFO:        https://graph.microsoft.com/oidc/userinfo
SCOPES:           openid profile email
```

### Auth0

```
ISSUER:           https://<tenant>.auth0.com
AUTHORIZATION:    {ISSUER}/authorize
TOKEN:            {ISSUER}/oauth/token
USER_INFO:        {ISSUER}/userinfo
```

## 相关文档

- [配置参考 - SSO](../configuration.md#十sso) — 全部 env 变量
- [JWT](jwt.md) — SSO 之后的会话
- [审计日志](audit.md) — 登录审计字段
- [权限模型](../permissions.md) — SSO 用户的角色分配
