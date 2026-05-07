# 密钥轮换 SOP

Rudder 有 3 把共享密钥。任一把泄漏或怀疑泄漏必须立刻轮换。

| Env                    | 用途                                                            | 长度  | 影响面                                       |
|------------------------|-----------------------------------------------------------------|-------|----------------------------------------------|
| `RUDDER_JWT_SECRET`    | 签发 / 校验登录 Token                                            | ≥ 32  | 所有已登录用户（轮换即强制登出）              |
| `RUDDER_ENCRYPT_KEY`   | AES 加密 datasource 凭证 (`t_r_datasource.credential` 列)        | ≥ 32  | 所有已保存的数据源配置（密文需要用旧 key 解） |
| `RUDDER_RPC_AUTH_SECRET` | Server ↔ Execution 的 RPC HMAC 签名                             | ≥ 32  | 集群间 RPC（Server 和 Execution 必须相同）    |

---

## 生成强密钥

```bash
# 任一都行，至少 32 字节
openssl rand -base64 48
head -c 48 /dev/urandom | base64
```

**拒绝** 使用字典词、可记忆短语、示例里的 `change-this-to-...`。`SecurityConfigValidator` 启动时会校验长度和黑名单。

---

## 1. `RUDDER_JWT_SECRET` 轮换（最简单）

无状态：Token 解码依赖 secret，换掉后旧 Token 全部失效，所有用户需要重新登录。

1. 生成新 secret
2. 在所有 Server 节点的 `.env` 把 `RUDDER_JWT_SECRET` 换成新值
3. 逐个滚动重启 Server（Execution 不依赖 JWT，不用动）
4. 通知用户重新登录

**零停机** — 滚动期间请求会在"已切换 secret 的节点"上被 401，前端会引导到登录页。

---

## 2. `RUDDER_ENCRYPT_KEY` 轮换（最复杂）

**有状态**：`t_r_datasource.credential` 列里的密文是用旧 key 加密的。直接换新 key 会导致所有 credential 解密失败 → 所有数据源不可用。

步骤：

1. 备份 DB（必须）
   ```bash
   mysqldump rudder t_r_datasource > datasource-$(date +%F).sql
   ```
2. 停 API / Execution（整个服务窗口）
3. 用旧 key 写一次性脚本：读 `t_r_datasource.credential` → 解密 → 再用新 key 加密 → 回写
   - 脚本模板：`tools/rotate-encrypt-key.sh`（按需补齐；没有时用 `CredentialService` 的 `encrypt/decrypt` 单独写个 CLI）
4. 把 `.env` 的 `RUDDER_ENCRYPT_KEY` 换成新值
5. 启动服务，手动抽查 1 个数据源做 "Test Connection"
6. 确认后再把旧备份归档留存 ≥ 30 天


---

## 3. `RUDDER_RPC_AUTH_SECRET` 轮换

Server 和 Execution 之间的 RPC 调用带 HMAC-SHA256 签名。两边 secret 必须一致，否则 `RpcServerHandler` 拒签直接 401。

1. 生成新 secret
2. **同时**在所有 Server 和 Execution 节点的 `.env` 改好（不要一边先改）
3. 尽量接近同时地滚动重启全部节点。过渡期（节点 A 换完、B 没换）会出现 RPC 签名校验失败，Server 端看到 `RpcAuthException`
4. 确认所有节点日志里不再有 `RpcAuthException` 即完成

---


---

## 轮换后验证 Checklist

- [ ] 登录一次前端，Token 能拿到
- [ ] 任一数据源 "Test Connection" 通过
- [ ] 前端"执行一个 mysql 脚本"，Server 能派发给 Execution 并拿到结果
- [ ] 查看 `t_r_audit_log` 最近记录，有新登录 / 执行条目
- [ ] `grep RpcAuthException` 近 5 分钟 Server + Execution 日志应为空
