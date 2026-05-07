# AI 评测(Eval)测试指南

> 用途:验证 AI agent 在真实链路下的行为一致性。每次改动 agent / ContextBuilder / Tool / Advisor / Prompt 模板,都应该先跑一批 eval 回归,再发布。

---

## 1. 为什么要有 eval

AI agent 的正确性由**多个模块合作产生**:

```
用户消息
  → ContextBuilder 注入 (dialect / schema / pinnedTables / selection / script ...)
  → RudderRagAdvisor 召回相关文档
  → RedactionAdvisor 脱敏
  → ChatClient.stream + Tool execution loop
  → RudderToolCallback (权限 / 审批 / 真实执行)
  → LLM 输出
```

任一环节出问题 —— dialect 没生效、RAG 召回错、工具没注册、pinnedTables 没注入 —— 最终生成的 SQL 就不对。手工点击测一遍成本高,覆盖不全。

**eval = 自动化跑真实链路 + 多维度断言**,用来快速回归。

---

## 2. 执行路径

eval 走的是**和生产 agent 几乎一样的链路**,只在两个地方不同:

| 维度 | 生产 agent | eval(`EvalExecutor`) |
|------|----------|------|
| System prompt 构建 | 真实 `ContextBuilder` | 真实 `ContextBuilder` |
| RAG advisor | 真实 | 真实 |
| 工具集 | 真实 `ToolRegistry` | 真实 `ToolRegistry` |
| Tool callback | `RudderToolCallback`(权限 / 审批 / 落库) | `EvalToolCallback`(跳过权限审批,只采集调用轨迹) |
| Session / 消息持久化 | 落 `t_r_ai_session` / `t_r_ai_message` | **零写入**,全内存 |

所以 eval 跑 100 次不会污染任何业务数据,也不会触发工具审批弹窗,但**会真实调用工具**(list_tables / describe_table / run_sql_readonly 会真的打到数据源)。

`EvalMode` 枚举只有两个值:`AGENT` 和 `CHAT`,默认 AGENT。

---

## 3. 断言模型

每个 case 的 `expectedJson`(`ExpectedSpec`)是一个 spec,断言分三组(实现见 `EvalVerifier`):

### 文本断言

| 字段 | 类型 | 含义 |
|------|------|------|
| `sqlPattern` | string(regex) | 正则(不区分大小写 + DOTALL),`find` 匹配即过 |
| `mustContain` | string[] | 每个关键字必须出现(不区分大小写) |
| `mustNotContain` | string[] | 每个关键字都不能出现 |

### 工具调用断言(仅 AGENT 模式有意义)

| 字段 | 类型 | 含义 |
|------|------|------|
| `mustCallTools` | string[] | 每个工具必须至少被调用过一次(去重比较) |
| `mustNotCallTools` | string[] | 每个工具都不能被调用(黑名单) |
| `minToolCalls` | int | 工具调用总次数下限(含重复) |
| `maxToolCalls` | int | 工具调用总次数上限 |

### 性能断言

| 字段 | 类型 | 含义 |
|------|------|------|
| `maxLatencyMs` | int | 总耗时上限 |
| `maxTokens` | int | prompt + completion tokens 总和上限 |

**所有失败原因独立累计**:UI 上能一次看全所有问题,不是命中第一条就 early-return。

---

## 4. Demo Case

以下两个 case 建议作为**最小可用测试套件**,第一次启用 eval 时先建这两个跑通。

### Case 1:SQL_GEN · AGENT 模式(主力 case)

验证 agent 工具链 + dialect 注入 + pinnedTables 注入 + RAG 是否都正常协作。

| 字段 | 值 |
|------|---|
| Category | `SQL_GEN` |
| Difficulty | `MEDIUM` |
| Mode | `AGENT` |
| Datasource | 选一个 MySQL 数据源,如 `prod-mysql` |
| Task Type | `MYSQL`(注:实际枚举值;dialect 文件名为 `MYSQL_SQL.md`,DialectService 内部映射) |
| Prompt | `统计 orders 表最近 7 天每天的订单数量,按日期升序` |
| Active | `on` |

**上下文**:

| 字段 | 值 |
|------|---|
| Selection | (留空) |
| Pinned Tables | `bi.orders` |

**断言**:

```json
{
  "sqlPattern": "SELECT.*COUNT.*FROM.*orders.*GROUP\\s+BY",
  "mustContain": ["COUNT", "GROUP BY", "orders"],
  "mustNotContain": ["DROP", "DELETE", "TRUNCATE", "UPDATE"],
  "mustCallTools": ["describe_table"],
  "mustNotCallTools": ["run_sql_readonly"],
  "minToolCalls": 1,
  "maxToolCalls": 4,
  "maxLatencyMs": 30000,
  "maxTokens": 3000
}
```

**这个 case 能覆盖的链路问题**:

| 失败原因 | 说明哪里坏了 |
|---------|------------|
| `mustCallTools missing: describe_table` | Agent 没被激励去先查表结构 → prompt 里的引导或工具注册问题 |
| `mustNotCallTools violated: run_sql_readonly` | Agent 把测试环境当生产 → system prompt 没说清 eval 语境 / readOnly 约束 |
| `sqlPattern mismatch` | 可能是 dialect 没注入(输出了 Trino 语法) / pinnedTables 没注入(没找到 orders 表) |
| `mustContain missing: GROUP BY` | Agent 没理解"每天的"要求 → prompt quality 或模型能力 |
| `mustNotContain violated: DELETE` | Redaction / 提示词安全性问题 |
| `maxToolCalls exceeded: actual > 4` | Agent 陷入工具调用死循环 → skill / RAG 噪声太多 |
| `maxLatencyMs exceeded` | prompt 越写越长 / 工具执行慢 / LLM 模型变慢 |

---

### Case 2:EXPLAIN · CHAT 模式(对照 case)

不连工具、不连数据源,验证 CHAT 路径的 advisor 基础链路。

| 字段 | 值 |
|------|---|
| Category | `EXPLAIN` |
| Difficulty | `EASY` |
| Mode | `CHAT` |
| Datasource | (留空) |
| Task Type | (留空) |
| Prompt | `什么是 SQL 的 GROUP BY?用一句话解释` |
| Active | `on` |

**上下文**:全部留空

**断言**:

```json
{
  "mustContain": ["分组"],
  "mustNotContain": ["DROP", "DELETE"],
  "maxLatencyMs": 10000,
  "maxTokens": 500
}
```

**用途**:如果这个 case 都 fail,说明 AI provider 配置坏了 / advisor 链路崩了,不是 agent 问题。两个 case 搭配能分辨"是 agent 坏了还是更基础的东西坏了"。

---

## 5. 如何用 eval 调试

### 场景 A:改了 `ContextBuilder` 后怀疑 dialect 注入坏了

- 用 Case 1(带 `TaskType=MYSQL` 的 AGENT case)
- 看 finalText:如果输出是 Trino 或 StarRocks 风格的 SQL,dialect 没生效
- 对比 `run_detail` 里的 final text 和预期

### 场景 B:加了新工具 `xxx_tool`,想确认工具被 agent 正确发现了

- 建一个新 case,prompt 引导 AI 去用这个工具
- 断言 `mustCallTools: ["xxx_tool"]`
- 跑一次,如果 failReason 是 `mustCallTools missing: xxx_tool`,说明工具没注册到 `ToolRegistry` 或 system prompt 里没提示

### 场景 C:优化 prompt 模板想确认没回归

- 保留一批稳定的 case(sqlPattern + mustContain)
- 改完 prompt 跑批,看哪些之前 pass 的现在 fail
- 去 run_detail 看 failReason 和 finalText 找根因

### 场景 D:想压测 agent 性能

- 建一批 case 统一 `maxLatencyMs: 5000`
- 跑批看哪些超时,定位慢 prompt / 慢工具

---

## 6. Run 结果如何解读

在 UI 上点 case 的 "History" → 某一行 → 抽屉展开,看到:

1. **顶部状态**:PASS/FAIL + provider + model + latency + tokens
2. **Fail Reasons** 区块(FAIL 时才有):所有失败原因列表,**从上到下按发生顺序**
3. **Final Reply**:LLM 最终完整回复(对应 `t_r_ai_eval_run.finalText`)
4. **Tool Call Sequence**:工具调用按顺序编号(`toolCallsJson`)
   - 每个可展开看 input / output / error
   - 颜色:绿色 success / 红色 failed

调试思路:**先看 Fail Reasons 定位失败维度 → 再看 Final Reply 确认真实输出 → Tool Call 序列查 agent 决策链**。

---

## 7. 跑批触发方式

### UI

管理后台 → AI 配置 → "测评" tab → 右上角 "跑一批"。

### API

`AiEvalController` 提供:
- `GET /api/ai/eval/cases` 列 case
- `POST /api/ai/eval/cases` 新建 case
- `POST /api/ai/eval/batches` 跑批
- `GET /api/ai/eval/batches/{batchId}` 查批次结果
- `GET /api/ai/eval/cases/{caseId}/runs` 查某 case 历史

```bash
POST /api/ai/eval/batches?category=SQL_GEN
```

返回包含 `batchId / total / passed / failed / runs[]`。

---

## 8. 最佳实践

### 建 case 的原则

1. **一个 case 一个关注点**:别把 10 个断言堆一个 case;拆开更容易定位问题
2. **命名清晰**:category + taskType + difficulty 让你一眼看出这个 case 测什么
3. **优先级分组**:
   - 冒烟:3-5 个 case,上线前必跑
   - 回归:20+ 个 case,每次改 agent 相关代码跑
   - 性能:专用 latency / token 上限的 case,周期性跑
4. **失败阈值要宽松**:别把 `maxLatencyMs=1000` 卡得太死,LLM 服务本身有波动

### 不建议的 case

| 反模式 | 原因 |
|-------|------|
| 只写一个超宽泛的 `sqlPattern` | 等于没测,SELECT 随便写都过 |
| `mustContain: ["应该"]` | 断言人类语言,LLM 输出变化大,易假阴性 |
| 用 eval 测 LLM 的幻觉率 | LLM 本身就是概率,单次结果说明不了问题。想测概率得跑 100 次统计 |
| 禁用 `run_sql_readonly` 的 case 里 prompt 让它 "查一下数据" | 要求自相矛盾,自然 fail,没意义 |

---

## 9. 一致性保障

eval 保证**结果能代表生产 agent 行为**的前提:

- 复用生产 `ChatClientFactory` / `ContextBuilder` / `LlmPluginManager` / `ToolRegistry`
- 所有 Advisor(Logger / RAG / Redaction / UsageMetrics)正常挂载
- 工具集和生产相同,**唯一差异**是 callback 不写库

**eval 不能覆盖的场景**:
1. **工具审批流**:`EvalToolCallback` 跳过了 `ToolApprovalRegistry`,所以"等用户批准"这条路径测不了
2. **SSE 流式分片**:eval 不走 SSE,token 流式相关的 bug 测不出来
3. **跨 session 历史上下文**:eval 无 session,`messages` 列表为空
4. **真实用户 auth**:eval 没绑定 `userId`,权限检查会按匿名走

这些场景需要手工测或集成测试补充。

---

## 10. 故障排查

| 症状 | 可能原因 | 排查方向 |
|------|---------|---------|
| 所有 case 都执行报错 | AI provider 没配置 | 管理后台 → AI 配置 → AI 大模型 配一个 provider |
| 所有 case 都连接失败 | Redis / 元数据服务 / 数据源不通 | 检查基础设施 |
| 某 case 突然从 pass 变 fail,但 prompt 和断言都没改 | 可能 LLM 模型更新 / provider 服务波动 | 跑 2-3 次看是否稳定;如果确定是 provider 的问题,调高 `maxLatencyMs` 或改 `sqlPattern` 更宽松 |
| `mustCallTools missing: describe_table` 但工具确实注册了 | 可能 prompt 没引导 agent 去用;或工具 description 不清晰 | 看 Tool Call 序列,确认 agent 到底调了啥;调整工具 description 或 case prompt |

---

## 附录:JSON 参考

### Case 完整 JSON(API 创建格式)

```json
{
  "category": "SQL_GEN",
  "taskType": "MYSQL",
  "difficulty": "MEDIUM",
  "mode": "AGENT",
  "datasourceId": 1,
  "engineType": "MYSQL",
  "workspaceId": null,
  "prompt": "统计 orders 表最近 7 天每天的订单数量,按日期升序",
  "contextJson": "{\"pinnedTables\":[\"bi.orders\"]}",
  "expectedJson": "{\"sqlPattern\":\"SELECT.*COUNT.*FROM.*orders.*GROUP\\\\s+BY\",\"mustContain\":[\"COUNT\",\"GROUP BY\",\"orders\"],\"mustNotContain\":[\"DROP\",\"DELETE\",\"TRUNCATE\",\"UPDATE\"],\"mustCallTools\":[\"describe_table\"],\"mustNotCallTools\":[\"run_sql_readonly\"],\"minToolCalls\":1,\"maxToolCalls\":4,\"maxLatencyMs\":30000,\"maxTokens\":3000}",
  "active": true
}
```

### Run 完整字段(`t_r_ai_eval_run`)

```
batchId              批次 ULID
caseId               关联 case
provider             AI provider(CLAUDE / OPENAI / ...)
model                具体模型名
passed               true/false
score                BigDecimal
finalText            (LLM 完整回复)
toolCallsJson        JSON[]   工具调用序列
failReasonsJson      JSON[]   失败原因列表(passed=true 时为 [])
latencyMs            int
promptTokens         int
completionTokens     int
```
