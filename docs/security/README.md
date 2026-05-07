# 安全

> Rudder 平台的安全机制速览。生产部署前请逐项确认。

## 文档

| 主题 | 文档 |
|:---|:---|
| 登录与会话（JWT） | [jwt.md](jwt.md) |
| SSO（OIDC / LDAP） | [sso.md](sso.md) |
| 审计日志 | [audit.md](audit.md) |
| 密钥轮换 SOP | [rotation.md](rotation.md) |
| 数据脱敏 | [../redaction.md](../redaction.md) |
| 权限模型（RBAC） | [../permissions.md](../permissions.md) |
| 数据源凭证加密 | [../datasource.md#凭证加密](../datasource.md#凭证加密) |

## 总览

```
┌── 鉴权层 ─────────────────────────────────────┐
│  本地账号 + BCrypt   ┐                         │
│  OIDC（OAuth2 授权码）├─→ AuthService ─→ JWT  │
│  LDAP / AD            ┘                         │
└────────────────────────────────────────────────┘
                  ↓ JWT bearer
┌── 拦截层 ─────────────────────────────────────┐
│  PermissionInterceptor                          │
│   ├─ verifyWith(jwtKey)  + 过期判定             │
│   ├─ 解析 UserContext (userId / isSuperAdmin)   │
│   └─ 配合 @RequireRole 校验 RBAC 等级           │
└────────────────────────────────────────────────┘
                  ↓
┌── 业务层 ─────────────────────────────────────┐
│  @AuditLog 切面 → t_r_audit_log（异步）        │
│  RPC HMAC-SHA256（Server↔Execution）           │
│  RedactionService（结果集 / AI / 日志 出口）   │
│  RUDDER_ENCRYPT_KEY AES-CBC（数据源凭证）      │
└────────────────────────────────────────────────┘
```

## 三把强密钥

启动时由 `SecurityConfigValidator` 校验，缺失或长度不足直接拒绝启动：

| Env | 用途 | 长度 |
|:---|:---|:---|
| `RUDDER_JWT_SECRET` | 签 / 验登录 JWT | ≥ 32 字节 |
| `RUDDER_ENCRYPT_KEY` | AES 加密数据源凭证 / 第三方 token | ≥ 32 字节 |
| `RUDDER_RPC_AUTH_SECRET` | Server↔Execution RPC HMAC | ≥ 32 字节，两端必须一致 |

**禁用** `change-this-to-...` 等示例值；轮换流程见 [rotation.md](rotation.md)。

## 登录限流

`POST /api/auth/login` 按客户端 IP 计数，**1 分钟最多 10 次**（`AuthController.LOGIN_MAX_PERMITS / LOGIN_WINDOW_MS`）。超限抛 `TooManyRequestsException`，对暴力破解 / credential stuffing 兜底。

LDAP 走同一条限流（`/sso/ldap`），OIDC 因走 IdP 重定向不在该限流内（IdP 自己负责）。

## 默认账号

`data.sql` 种子带 `admin / admin123`，`is_super_admin = true`。

**生产部署务必**：

1. 改 `RUDDER_SQL_INIT_MODE=never`，禁止自动初始化
2. 首次登录后立刻改密 / 禁用，或新建 SUPER_ADMIN 后删默认账号

## 生产 Checklist

- [ ] 三把密钥替换为强随机串（≥ 32 字节）
- [ ] 默认 admin 账号改密或禁用
- [ ] `RUDDER_LOG_LEVEL=INFO`，敏感字段不进 DEBUG 日志
- [ ] OIDC `redirect_uri` 用 HTTPS 域名，状态使用 `OneShotTokenService`（已内置）
- [ ] LDAP 用 `ldaps://` 或仅在 VPC 内访问；`trust-all-certs` 仅测试用
- [ ] 反代加 IP 白名单 / 速率限制，遮挡 `/actuator/**`
- [ ] 数据源使用最小权限账号；密码长度 ≥ 16
- [ ] 配置脱敏规则覆盖 PII（[redaction.md](../redaction.md)）
- [ ] 审计表 `t_r_audit_log` 有归档策略（[audit.md](audit.md)）
- [ ] 时钟同步（NTP），RPC HMAC 5min 防重放窗口对偏差敏感
- [ ] DB / Redis / Vector Store 走独立网段，外网不直接可达

## 上报安全问题

发现安全漏洞请直接发 GitHub issue 标 `security` 标签，或私信仓库维护者。请勿在公开 issue 详细披露未修复漏洞。
