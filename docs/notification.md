# 消息通知

> Rudder 通过 Notification SPI 把平台事件(审批流转、节点上下线)推送到外部 IM。当前内置飞书、钉钉、Slack 三种 provider,平台级单 active(同一时刻仅一个 provider 启用)。

## 配置入口

`管理 → 通知渠道`。提交即生效,无须重启服务。

配置落地:`t_r_spi_config` 表,`type='NOTIFICATION'` + `provider IN ('LARK','DINGTALK','SLACK')`,UNIQUE KEY `(type, provider)`。保存任一 provider 时同 type 其余行的 `enabled` 自动置 0,以维持单 active 不变量。

## Provider 字段

各 provider 的字段定义、webhook 获取步骤与安全策略选项,由 SPI guide 在管理后台表单内嵌呈现,源文件位于 `rudder-spi/rudder-notification/rudder-notification-<provider>/src/main/resources/spi-guide/`。三种 provider 当前字段集如下:

| Provider | 必填字段 | 可选字段 |
|:---|:---|:---|
| Lark | `webhookUrl` | — |
| DingTalk | `webhookUrl` | `secret`(启用加签时填) |
| Slack | `webhookUrl` | — |

## 触发事件

| 事件类型 | 触发场景 | Notification Level |
|:---|:---|:---|
| `APPROVAL` | 工作流发布审批提交 / 通过 / 驳回(`ApprovalSubmittedMessage` / `ApprovalApprovedMessage` / `ApprovalRejectedMessage`) | INFO / SUCCESS / WARN |
| `NODE_ONLINE` | EXECUTION 节点首次注册或冷启后 PostConstruct 完成 | SUCCESS |
| `NODE_OFFLINE` | EXECUTION 节点优雅下线(SIGTERM/PreDestroy)或被心跳回收机制翻为 OFFLINE | WARN / ERROR |

所有事件统一进 `NotificationService.notify(NotificationMessage)`,由 active sender 渲染并发送。

## 运行时行为

- **异步发送**:通知发送走 `@Async` 线程池,失败仅记录 ERROR 日志,不阻塞业务流程。
- **单 active**:同 type 下仅 `enabled=1` 的一行参与发送,当前 sender 经全局缓存解析。
- **配置变更广播**:配置写入后通过 Redis pub/sub 失效所有节点的本地缓存,多副本即时生效,无须重启。
- **未配置降级**:`enabled=0` 或无任何 NOTIFICATION 行时,事件被静默丢弃并记 DEBUG 日志,不抛异常。

## 健康检查

`NotificationService.health()` 调用当前 active sender 的 `healthCheck()`,返回 `HealthStatus`(`UP / DOWN / UNKNOWN`)。管理后台通知渠道页面展示该状态。

`UNKNOWN` 表示未启用任何 provider 或 sender 不支持探活;`DOWN` 通常意味着 webhook URL 不可达或返回非 2xx。

## 故障排查

| 现象 | 可能原因 | 排查方向 |
|:---|:---|:---|
| 完全无通知 | 未启用 provider / `enabled=0` | 检查 `t_r_spi_config` 当前 NOTIFICATION 行 |
| 通知延迟 | `@Async` 线程池满 | 调高 `RUDDER_ASYNC_MAX_SIZE` / `RUDDER_ASYNC_QUEUE_CAPACITY`(见 [configuration.md](configuration.md) 六) |
| 钉钉报 `sign not match` | 已开启加签但未填 `secret`,或 `secret` 与机器人配置不一致 | 重新复制机器人加签 secret |
| 飞书报 `not in whitelist` | webhook 启用 IP 白名单但 Rudder 出口 IP 未加入 | 加白 Server 节点公网 IP |
| 多副本各发一条 | 事件源头未去重(如多个 Server 同时检测到节点离线) | 节点回收的 CAS 已保证每个 stale 行仅一个节点 affected=1;若仍出现重复推送,请通过 issue 反馈 |
| 配置改后未生效 | Redis pub/sub 不通 | 检查 Redis 健康 + 各节点订阅 `rudder:signal:global-cache` 频道 |

## 扩展

新增 IM provider 步骤参见 [SPI 开发指南](spi-guide.md):

1. 在 `rudder-spi/rudder-notification/` 下新建 `rudder-notification-<provider>` 模块
2. 实现 `NotificationSenderFactory` + `NotificationSender`,并通过 `@AutoService(NotificationSenderFactory.class)` 注册
3. 在 `getProvider()` 返回唯一 provider 标识(大写),`params()` 声明前端表单字段
4. 重新打包后管理后台通知渠道页面自动出现新选项,无须改 UI 代码
