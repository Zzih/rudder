# 权限模型

> Rudder 采用「平台 / 工作空间」两层 + 数据源单独授权的权限模型。本章覆盖角色、判定逻辑、典型授权矩阵。

## 三个独立维度

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. 平台级               t_r_user.is_super_admin                 │
│    SUPER_ADMIN 一刀切，跳过所有 workspace 检查                  │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│ 2. 工作空间级           t_r_workspace_member ( workspace, user, role ) │
│    一人多 workspace，每个 workspace 独立角色                    │
│    WORKSPACE_OWNER > DEVELOPER > VIEWER                         │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│ 3. 数据源授权           t_r_datasource_permission ( datasource, workspace ) │
│    workspace 维度，详见数据源文档                                │
└─────────────────────────────────────────────────────────────────┘
```

## 角色等级

`io.github.zzih.rudder.common.enums.RoleType`：

| 角色 | 等级 | 适用范围 | 含义 |
|:---|:---:|:---|:---|
| `SUPER_ADMIN` | 3 | 平台 | 平台超管，所有操作放行 |
| `WORKSPACE_OWNER` | 2 | 工作空间 | 空间 Owner，可管成员 / 改空间设置 / 发布 |
| `DEVELOPER` | 1 | 工作空间 | 开发者：建脚本 / 跑任务 / 编排工作流 |
| `VIEWER` | 0 | 工作空间 | 只读：看脚本 / 工作流 / 实例 / 日志 |

判定逻辑：

```
if user.isSuperAdmin → 通过（无视 workspace 检查）
else
    member = t_r_workspace_member where workspace=? and user=?
    if member == null → 拒绝
    if RoleType.of(member.role).level >= 接口要求等级 → 通过，否则拒绝
```

## 接口级声明

`@RequireRole(RoleType.X)` 注解写在 Controller 类或方法上，由 `PermissionInterceptor` 拦截。方法级 > 类级。

```java
@RestController
@RequireRole(RoleType.VIEWER)
public class ScriptController {

    @GetMapping("/{id}")
    public R<Script> get(...) { ... }   // VIEWER 即可

    @PostMapping
    @RequireRole(RoleType.DEVELOPER)
    public R<Long> create(...) { ... }  // 升级为 DEVELOPER
}
```

实际使用举例：

| Controller | 类级 | 个别方法 |
|:---|:---|:---|
| `AdminAuditLogController` | `SUPER_ADMIN` | — |
| `ConfigController.update*` | — | `SUPER_ADMIN` |
| `AiUserController` | `VIEWER` | 写操作升 `DEVELOPER` |
| `ApprovalController` | `VIEWER` | 提交 / 审批升 `DEVELOPER` |

> SPI 配置（AI / 文件 / 元数据 / 通知 / 审批 / 版本）类的 `update` 端点统一要求 `SUPER_ADMIN`，因为这些是平台级配置。

## 三类典型场景

### 用户 A 拥有 workspace 1 的 OWNER + workspace 2 的 DEVELOPER

```
SUPER_ADMIN: false
workspace 1 → WORKSPACE_OWNER → 能加成员、改设置、发布工作流
workspace 2 → DEVELOPER       → 只能开发，不能加成员
其它 workspace                 → 看不到
```

### SUPER_ADMIN

无视 `t_r_workspace_member`，所有 workspace 都能进、所有数据源都能看。注意：`SUPER_ADMIN` 是平台级身份，**与是否在某个 workspace 里没关系**。

### 默认管理员

`data.sql` 种子带 `admin / admin123`，`is_super_admin = true`。**生产部署务必改密或禁用**。

## 工作空间成员管理

```
POST   /api/workspaces/{id}/members         加成员（OWNER）
PUT    /api/workspaces/{id}/members/{uid}   改角色（OWNER）
DELETE /api/workspaces/{id}/members/{uid}   移出（OWNER）
```

成员管理需要 `WORKSPACE_OWNER`。但 `SUPER_ADMIN` 也能直接操作，作为兜底。

工作空间最多保留多少 OWNER 由前端 / 后端校验：删 / 降级最后一个 OWNER 会被拒绝，避免把 workspace 锁死。

## 平台用户管理

`/api/admin/users/*`，需要 `SUPER_ADMIN`：

- 创建 / 禁用 / 删除用户
- 设置 / 取消 `is_super_admin`
- 重置密码（本地账号）
- SSO 用户的本地档案（`sso_provider` / `sso_id`）由登录流程自动维护，管理员不直接编辑

## 数据源授权

数据源不归任何 workspace，统一在「平台 - 数据源」管理。授权关系存 `t_r_datasource_permission`，被授权的 workspace 内**所有成员**（不论角色）都能看到这个数据源；至于能不能在脚本 / 工作流中使用，仍要叠加 `WORKSPACE_OWNER / DEVELOPER` 的接口要求。

```
SUPER_ADMIN          → 所有数据源（无授权过滤）
WORKSPACE_*          → 仅当前 workspace 已授权的数据源
```

更多见 [数据源](datasource.md#workspace-授权)。

## 发布与审批

发布工作流（提交、审批、上线）需要 `WORKSPACE_OWNER`：

```java
if (RoleType.of(userInfo.getRole()).getLevel() < RoleType.WORKSPACE_OWNER.getLevel()) {
    throw new BizException(...);  // 没权限
}
```

审批渠道（飞书 / Slack / KissFlow）由 SPI 决定具体落地。审批通过的回调走 `t_r_approval_record`，回流给 Server。

## API 鉴权

### JWT 流程

```
登录（用户名/密码 或 SSO）
   → AuthService.login() 校验 → 颁发 JWT
   → 客户端在 Authorization: Bearer <token> 中携带
   → 网关侧 PermissionInterceptor:
       1. 解析 JWT，校验签名 + 过期时间
       2. 把 userId / username / isSuperAdmin 放到 UserContext
       3. 取请求路径里的 workspaceId（如 /workspaces/{id}/...）
       4. 查 t_r_workspace_member 解析当前 workspace 的 role
       5. 与 @RequireRole 比较，通过 / 拒绝
```

### Token 过期

默认 12 小时（`rudder.security.jwt-expiration=43200000`）。前端在 401 时跳转登录；refresh token 暂未实现。

### SSO（OIDC / LDAP）

- OIDC：标准授权码流程，回调到 `/api/auth/sso/callback?provider=oidc`，按 `email` 在 `t_r_user` 自动 upsert，绑定 `sso_provider=OIDC` + `sso_id=<sub>`
- LDAP：登录页直接输用户名密码，后端 BindRequest 校验 → 拉用户属性 → upsert 到 `t_r_user`

SSO 用户首次登录时默认无任何 workspace 成员关系——需 `WORKSPACE_OWNER` 或 `SUPER_ADMIN` 加入 workspace。

详见 [配置参考 - SSO](configuration.md#十sso)。

## 审计

所有写操作落 `t_r_audit_log`，字段：`user_id` / `username` / `module` / `action` / `target` / `ip` / `user_agent` / `payload`。

`/api/admin/audit-logs` 需要 `SUPER_ADMIN`。

支持按用户 / 模块 / 动作 / 时间过滤。详见 `AdminAuditLogController`。

## 授权矩阵速查

|                        | SUPER_ADMIN | WORKSPACE_OWNER | DEVELOPER | VIEWER |
|:---|:---:|:---:|:---:|:---:|
| 平台用户管理 | ✓ | | | |
| 平台审计日志 | ✓ | | | |
| SPI 配置（AI / 文件 / 通知 等） | ✓ | | | |
| 数据源 CRUD / 授权 | ✓ | | | |
| 创建工作空间 | ✓ | | | |
| 删除 / 改名工作空间 | ✓ | ✓ | | |
| 加 / 删 / 改 工作空间成员 | ✓ | ✓ | | |
| 发布工作流到生产 | ✓ | ✓ | | |
| 创建 / 编辑 脚本与工作流 | ✓ | ✓ | ✓ | |
| 触发任务 / 工作流实例 | ✓ | ✓ | ✓ | |
| 查看脚本 / 工作流 / 实例 / 日志 | ✓ | ✓ | ✓ | ✓ |
| 使用 AI 助手 | ✓ | ✓ | ✓ | ✓（只读类工具） |

## 排障

| 症状 | 排查 |
|:---|:---|
| 401 | JWT 过期或被篡改；前端 cookie / localStorage 清掉重登 |
| 403 | 当前 workspace 没成员关系，或角色不够；让 OWNER 把你加进去 |
| 看不到某 workspace | `t_r_workspace_member` 没记录；联系该 workspace OWNER |
| 看不到某数据源 | workspace 没授权该数据源；联系 `SUPER_ADMIN` 在「平台 - 数据源」加授权 |
| SSO 登录后没权限 | 首次登录默认无 workspace；需要 OWNER 加进 workspace 并赋角色 |

## 相关文档

- [配置参考](configuration.md) — JWT / SSO / LDAP
- [数据源](datasource.md#workspace-授权) — 数据源授权细节
- [MCP](mcp.md) — PAT token 与 capability 矩阵（角色 → 能力的第二维授权）
- [security/rotation.md](security/rotation.md) — JWT 密钥轮换
