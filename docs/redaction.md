# 数据脱敏

> Rudder 在所有数据出口（查询结果 / 日志 / AI 流式输出 / 工具 IO）统一调用 `RedactionService`，按管理员配置的规则做脱敏。本章覆盖规则模型、接入点、运维。

## 规则模型

```
t_r_redaction_rule          匹配什么 (TAG / COLUMN / TEXT)
   strategy_code  ────►  t_r_redaction_strategy   怎么脱（5 种 executor）
```

### `RedactionRuleType`

```java
enum RedactionRuleType { TAG, COLUMN, TEXT }
```

| 类型 | 命中条件 | 适用 |
|:---|:---|:---|
| `TAG` | 元数据平台（DataHub / OpenMetadata）打的 tag 命中（字符串 / 通配） | 结构化结果集，按字段元数据语义脱敏 |
| `COLUMN` | 结果集列名正则 | 结构化结果集，按列名脱敏 |
| `TEXT` | 自由文本正则 | 日志 / AI 输出 / 字符串值 |

### `RedactionExecutorType`

```java
enum RedactionExecutorType { REGEX_REPLACE, PARTIAL, REPLACE, HASH, REMOVE }
```

| executor | 行为 | 关键字段 |
|:---|:---|:---|
| `REGEX_REPLACE` | 正则 match + 带反向引用的替换模板 | `match_regex` / `replacement` |
| `PARTIAL` | 保留前 N 后 M，中间填 `mask_char` | `keep_prefix` / `keep_suffix` / `mask_char` |
| `REPLACE` | 整体替换为固定字符串 | `replace_value` |
| `HASH` | SHA-256 截取前 N 位 | `hash_length` |
| `REMOVE` | 置 null | — |

### 表结构

```
t_r_redaction_rule       ( id, name, description, type, pattern, strategy_code, priority, enabled )
t_r_redaction_strategy   ( id, code, name, description, executor_type,
                           match_regex, replacement,
                           keep_prefix, keep_suffix, mask_char,
                           replace_value, hash_length, enabled )
```

`strategy.code` 是规则关联用的稳定编码，跨环境一致；`rule.priority` 越大越优先（同 type 内）。

### 命中顺序

#### 结构化数据（一行 Map）

```
对每列：
  1. 先按 column tag 匹配 TAG rules
  2. 命中 → 用对应 strategy
  3. 未命中 → 按 column 名匹配 COLUMN rules
  4. 命中 → 用对应 strategy
  5. 都未命中 → 跳过该列（不递归扫值；TEXT 只对自由文本）
```

#### 自由文本

```
依次尝试所有启用的 TEXT rules（按 priority 降序），逐个 strategy.scrub
```

> TEXT rule 在自由文本上是叠加的（一段文本可能命中多条不同规则）；结构化列上是短路的。

## 全局接入点

### 1. 查询结果

`rudder-execution`：`QueryResultCollector`

```java
List<Map<String,Object>> rows = task.getResultRows();
List<ColumnMeta>          cols = ...;
redactionService.applyMapRows(cols, rows);   // 原地改写
```

任何 SQL / Spark / Flink SQL 任务的结果在落 `t_r_task_instance.result_path` / 上传 FileStorage 之前，都会经此一道。

### 2. AI 输出

`rudder-ai`：

| 接入点 | 处理什么 |
|:---|:---|
| `RedactionAdvisor` | LLM **完整响应**（after-advisor，全文 scrub 后入库） |
| `StreamingRedactor` | LLM **流式 token**（边发边脱敏，避免敏感 token 早于 scrub 推到前端） |
| `RudderToolCallback.scrubText` | **工具调用 result**（tool 返回的字符串先 scrub 再喂给 LLM） |

### 3. 日志

`rudder-service-shared`：`RedactingMessageConverter`

- Logback 注册自定义 `%msg` converter
- 在 appender 写文件 / stdout 之前对每条日志调 `RedactionService.scrubText`
- `LogRedactionBridge` 桥接 Spring 容器与 Logback 静态注册（容器还没起好时返回原文，不阻塞启动）

`logback-spring.xml` 配置示例：

```xml
<configuration>
  <conversionRule conversionWord="msg"
      converterClass="io.github.zzih.rudder.service.redaction.log.RedactingMessageConverter"/>

  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
</configuration>
```

之后所有 `%msg / %m / %message` 自动走脱敏。

### 4. 数据源凭证

数据源密码 / token 在 `RUDDER_ENCRYPT_KEY` 加密落库；响应 DTO 永远不返回密文，密码字段统一替换为 `***`（不走 `RedactionService`，由 DTO 层硬编码）。

## API

```java
public interface RedactionService {

    List<Map<String, Object>> applyMapRows(List<ColumnMeta> columns,
                                           List<Map<String, Object>> rows);

    Object applyValue(ColumnMeta column, Object value);

    String scrubText(String text);                       // TEXT 规则
    String previewStrategy(String code, String sample);  // 管理后台预览
}
```

实现：`LocalRedactionService`（`rudder-service-shared`），由 `RedactionRuleCache` 缓存规则，规则更新时 invalidate。

## 性能

- `applyMapRows` 先对 `columns` 做一次 dispatch 预计算（每列 → 命中的 strategy），逐行执行时直接查 map，避免反复正则匹配
- TEXT rule 编译后的 `Pattern` 缓存在 `RedactionRuleCache`
- 全局规则 < 200 条时无明显热路径开销
- 对大结果集（万行级）建议在 SQL 层就 SELECT 必要字段，减少需脱敏的列数

## 管理 UI

`/admin/redaction`，`SUPER_ADMIN` 可见：

- 规则列表（启用 / 停用 / 优先级 / pattern / strategy）
- 策略列表（按 executor 分组）
- 创建 / 编辑表单
- **预览**：输入 `code + sample` 看 `previewStrategy` 输出，验证策略效果
- 启用 / 停用立即生效（缓存 invalidate）

API 路由由 `RedactionController` + `RedactionAdminService` 提供，全部 `@RequireRole(RoleType.SUPER_ADMIN)`。

## 配置示例

### 银行卡号 PARTIAL

```
strategy:
  code = MASK_BANKCARD
  executor = PARTIAL
  keep_prefix = 6
  keep_suffix = 4
  mask_char   = *

rule:
  type     = COLUMN
  pattern  = ^(card_no|bank_card|账号)$
  priority = 100
  strategy_code = MASK_BANKCARD
```

`6228123412341234` → `622812******1234`

### 邮箱 REGEX_REPLACE

```
strategy:
  executor = REGEX_REPLACE
  match_regex = ^([^@]{2})[^@]*(@.+)$
  replacement = $1***$2

rule:
  type    = TEXT
  pattern = [\w\.-]+@[\w\.-]+
```

`alice.chen@example.com` → `al***@example.com`

### DataHub TAG → REPLACE

```
strategy:
  executor = REPLACE
  replace_value = [REDACTED]

rule:
  type    = TAG
  pattern = pii.high
```

任何被打了 `pii.high` tag 的列整体 → `[REDACTED]`。

## 与元数据集成

`TAG` 规则的命中依赖元数据 provider 把 tag 同步到 ColumnMeta：

- DataHub / OpenMetadata 拉取时把 `globalTags` 落到 `ColumnMeta.tags`
- JDBC provider 没有 tag 概念，TAG rule 自然不生效（COLUMN 规则兜底）
- 元数据缓存按 5min TTL，新打的 tag 最长 5 分钟生效

## 跨边界一致性

- **结构化数据**：脱敏在 Execution 端（写文件 / 回传 Server 之前），用户在 IDE 看到的就是脱敏后的内容
- **文件下载**：`/api/files/...` 返回 Execution 已落盘的脱敏文件，不会二次脱敏
- **AI 输出**：Server 侧脱敏（拿到 LLM token 流后立即处理）
- **审计 / 日志**：Logback converter 兜底，应用代码无需关心

任何**绕过 RedactionService 的导出路径**都是漏点。新增导出能力（如新增 ResultFormat / 新增 controller）时务必走平台 API（`/api/.../export`），不要直读 DB。

## 测试与验证

- 单元：`LocalRedactionServiceTest` 覆盖每个 executor + 优先级
- 集成：起一段示例日志，断言 `%msg` converter 的输出已脱敏
- 上线：在 admin UI 用「预览」功能拿真实样本验证

## 排障

| 症状 | 排查 |
|:---|:---|
| 配了规则但没生效 | `enabled=true`？规则缓存可能滞后，等几秒或重启 Server |
| 日志没脱敏 | `logback-spring.xml` 没 `<conversionRule conversionWord="msg" ...>` |
| AI 输出泄漏 PII | TEXT 规则没覆盖该模式；先在 admin UI 用样本预览 |
| 结果集列没脱敏 | 列名 / tag 是否命中 pattern；`COLUMN.pattern` 是否区分大小写 |
| 性能下降 | 规则数量爆炸；正则过于宽泛；用 `previewStrategy` 看慢规则 |

## 相关文档

- [配置参考](configuration.md#一密钥必填) — `RUDDER_ENCRYPT_KEY` 与脱敏的区别
- [AI 模块 - 架构](ai/architecture.md) — `RedactionAdvisor` 在 advisor 链路的位置
- [权限模型](permissions.md) — 谁能管理脱敏规则
