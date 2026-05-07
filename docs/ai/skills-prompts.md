# Skill & Prompt

> 这三样(Skill / Dialect / Context Profile)共同决定**发给 LLM 的 system prompt**长什么样。调优 AI 质量的主战场。

## System prompt 怎么拼出来的

每次 turn 开始,`ContextBuilder.build(request, session, profile)` 按顺序 append 多个 section,每段模板在 `ai-prompts/sections/*.md`(classpath):

```
[base-role]            base-role-agent.md / base-role-chat.md(按 mode 二选一)
[dialect]              当前 TaskType 的方言指导(仅 SQL/脚本类任务)
[active-script]        活跃 tab 的脚本正文(截 4000 字)
[selection]            编辑器当前选中文本(有的话)
[pinned-tables]        用户 Pin 的表 + workspace 级 Pin 的表 schema 摘要
[datasource]           活跃 tab 的数据源(host / type / ...)
[engine-visibility]    当前引擎允许访问的数据源列表
[tool-guidelines]      工具使用 / 输出格式规则(仅 AGENT 模式)
[custom-override]      profile.systemPromptOverride 覆盖或追加
[RAG-knowledge]        RudderRagAdvisor 在调用前再追加,紧跟上面所有段落之后
```

每段都是 **Spring AI PromptTemplate 渲染**(带变量),不是 Java 字符串拼接。改模板无需重新编译。模板文件实际清单:

```
ai-prompts/sections/
  active-script.md  base-role-agent.md  base-role-chat.md
  custom-override.md  datasource.md  engine-visibility.md
  pinned-tables.md  rag.md  selection.md  tool-guidelines.md
```

## Skill

**Skill** = 带结构化定义的"AI 能力模板"。表 `t_r_ai_skill` 存,字段:
- `name` / `displayName` / `description`
- `category`(CODE_GEN / DEBUG / OPTIMIZE / EXPLAIN / ...)
- `definition`(markdown prompt,决定触发时注入什么指导)
- `requiredTools`(JSON 字符串,这个 skill 期望使用的工具列表)
- `inputSchema`(可选,用于 Skill-as-tool 场景)
- `modelOverride`(可选,该 skill 想强制用某 model)
- `enabled`

> 平台级 vs workspace 级共用 `t_r_ai_skill` 表,通过 `workspaceId` 区分:`NULL` 是平台级,有值则只对该 workspace 生效。

### 平台内置 Skill(代码默认,启动加载)

| name | category | 触发 | 内容 |
|------|----------|------|------|
| `generate_task_code` | CODE_GEN | 用户让 AI 生成 SQL/Python/Shell | 按 pinned 表 + metadata 生成,遵循 dialect |
| `debug_failed_execution` | DEBUG | 任务执行失败了让 AI 看看 | 自动读 log → 定位 → 修复建议 |
| `optimize_sql` | OPTIMIZE | "帮我优化这条 SQL" | EXPLAIN → 找瓶颈 → 重写 |

> 实际启用清单以 `SkillRegistry` 启动扫描 + DB 覆盖为准。

### 自定义 Skill

**Admin → AI 配置 → Skills**(`POST /api/ai/skills`)。

| 字段 | 说明 |
|------|------|
| name | 英文标识(如 `generate_metric_def`),工程唯一 |
| displayName | 中文展示名 |
| category | 任选一个分类 |
| definition | Markdown prompt,会注入 system prompt(见下) |
| requiredTools | 此 skill 期望使用的工具 |
| enabled | on/off |

### Skill 触发机制

两种:

1. **Skill-as-tool**:`SkillToolProvider` 把启用 skill 包装成 `SkillAgentTool` 注入 ToolRegistry,LLM 自己按需调用
2. **前端硬指定**:`TurnRequest.contextTaskType` 等字段引导 ContextBuilder 走特定路径

### 热加载

Admin UI 改完保存 → SkillRegistry 刷新 → **下一个 turn 立即生效**。

## Dialect(SQL 方言)

每种 SQL 引擎的语法 / 内置函数 / 最佳实践不一样,方言帮 LLM 生成地道的 SQL。表 `t_r_ai_dialect` 存覆盖,出厂默认在 classpath `ai-prompts/dialects/{taskType}.md`。

### 当前出厂的方言文件

```
ai-prompts/dialects/
  MYSQL_SQL.md  STARROCKS_SQL.md  TRINO_SQL.md  HIVE_SQL.md
  SPARK_SQL.md  FLINK_SQL.md      PYTHON.md     SHELL.md  SEATUNNEL.md
```

> 注:实际 TaskType 枚举里 MySQL 的值是 `MYSQL`(不是 `MYSQL_SQL`),但 dialect 文件命名沿用了 `MYSQL_SQL.md`。`DialectService.loadContent(taskType)` 内部做了映射。

### 配置

**Admin → AI 配置 → 方言(Dialects)**(`GET/PUT/DELETE /api/ai/admin/dialects`)。

- 每个 TaskType 一条,UI 显示"默认"或"已覆盖"
- 点编辑修改 prompt content
- 点重置(DELETE)恢复代码默认

最终内容 = `t_r_ai_dialect` 中 enabled 行 → 否则回退 classpath 默认。

### 触发

`ContextBuilder.build` 里:仅当 TaskType 属于 SQL / 脚本类才 `append(dialect)`。其他(CHAT / WIKI 等)跳过,不浪费 token。

## Context Profile

**每 workspace / session 一条**配置(表 `t_r_ai_context_profile`,`scope` ∈ `WORKSPACE|SESSION`,`scopeId` 是 workspaceId 或 sessionId)。控制 system prompt 的开关型行为。

实际字段(以 `AiContextProfile` entity 为准):

| 字段 | 类型 | 作用 |
|------|------|------|
| `injectSchemaLevel` | string | `NONE` / `TABLES` / `FULL` —— schema 注入粒度 |
| `maxSchemaTables` | int | 注入 schema 的表数量上限 |
| `injectOpenScript` | bool | 是否注入活跃脚本正文 |
| `injectSelection` | bool | 是否注入编辑器选中 |
| `injectWikiRag` | bool | 是否触发 RAG 自动注入 |
| `injectHistoryLast` | int | 携带最近 N 条历史 |

> session 上的 `systemPromptOverride` 字段(在 `t_r_ai_session`)负责追加自定义文本到 system prompt 尾部。其他控制(允许工具白名单、ragTopK 等)目前由代码默认或上层调用方传参,不在 profile 表里持久化。

### 优先级

```
session profile (scope=SESSION) > workspace profile (scope=WORKSPACE) > 代码默认
```

session 创建时从 workspace profile 拷贝一份快照,之后可独立调。

### 端点

- `GET /api/ai/context-profiles/{scope}/{scopeId}` 取
- `PUT /api/ai/context-profiles` 写
- `DELETE /api/ai/context-profiles/{scope}/{scopeId}` 重置

### 使用场景

| 场景 | 配法 |
|------|------|
| 性能敏感 workspace 关掉 RAG | `injectWikiRag: false` |
| 限制 schema 注入规模 | `injectSchemaLevel: TABLES`, `maxSchemaTables: 10` |
| 关掉自动注入活跃脚本 | `injectOpenScript: false` |
| 减少历史回放 | `injectHistoryLast: 6` |
| workspace 个性化系统提示 | 在该 workspace 的 session 设 `systemPromptOverride`(走 session 表) |

## Pinned Tables

两层(表 `t_r_ai_pinned_table`,`scope` 区分):

| 层级 | 作用域 | 可配人 |
|------|-------|-------|
| **用户级 Pin** | 仅本人看得到,每次对话都注入 | 任何登录用户,在 IDE 元数据面板表节点 ⭐ |
| **Workspace 级 Pin** | 该 workspace 所有用户都注入 | WORKSPACE_OWNER / SUPER_ADMIN |

注入内容 = 表的**完整 schema 摘要**(列 + 类型 + 注释)。太多表会占 token,建议 Pin 核心业务表(一般 <10 张)。

端点:`GET / POST /api/ai/pinned-tables`,`DELETE /api/ai/pinned-tables/{id}` 或 `DELETE /api/ai/pinned-tables`(批量)。

## 调试 system prompt

Prompt 生成结果**不会发给前端**,但可以:

1. 日志:`SimpleLoggerAdvisor` 在 DEBUG 级别打印完整 prompt
2. 用 **eval** 反向验证(见 [评测](eval.md))
3. 临时改 `application.yml`:
   ```yaml
   logging:
     level:
       org.springframework.ai.chat: DEBUG
   ```

## 相关文档

- [评测](eval.md):通过 eval 验证 prompt 变更是否回归
- [知识库](knowledge-base.md):RAG 注入细节
- [开发者指南 - Pin 表](user-guide.md)

## 相关表

- `t_r_ai_skill` — skill(平台级 / workspace 级共表)
- `t_r_ai_dialect` — dialect 覆盖
- `t_r_ai_context_profile` — workspace / session profile
- `t_r_ai_pinned_table` — pinned 表
