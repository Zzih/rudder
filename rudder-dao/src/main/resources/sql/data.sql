-- ==================== Seed Data ====================
-- 最小启动种子：admin 用户 + SPI 平台配置默认值。

-- ===================== 1. Admin User =====================
-- 默认管理员账号：admin / rudder123（生产必须首次登录后修改）
INSERT IGNORE INTO `t_r_user` (`id`, `username`, `password`, `email`, `is_super_admin`, `created_by`, `created_at`)
VALUES
  (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@rudder.io', 1, 0, NOW());

-- ===================== 1.1 Default Authentication Source =====================
-- PASSWORD 是系统行(is_system=1),用户不可删/不可禁。OIDC/LDAP 行由管理员在 UI 增删。
INSERT IGNORE INTO `t_r_auth_source` (`id`, `name`, `type`, `enabled`, `is_system`, `priority`, `config_json`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES (1, '本地账号', 'PASSWORD', 1, 1, 0, NULL, 1, NOW(), 1, NOW());

-- ===================== 2. SPI Platform Configs =====================
-- 所有 SPI 走统一表 t_r_spi_config，按 type 区分(FILE / RESULT / RUNTIME / METADATA /
-- VERSION / APPROVAL / PUBLISH / NOTIFICATION / LLM / EMBEDDING / VECTOR / RERANK)。
-- default_query_rows 这类特殊字段编入 provider_params JSON。
-- 启动种子 INSERT IGNORE 保证幂等；用户在 UI 修改后 DB 行更新，种子不会覆盖。

INSERT IGNORE INTO `t_r_spi_config` (`id`, `type`, `provider`, `provider_params`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES
  (1, 'METADATA', 'JDBC',    '{}',                                          1, 1, NOW(), 1, NOW()),
  (2, 'FILE',     'LOCAL',   '{"basePath":"/tmp/rudder/files"}',            1, 1, NOW(), 1, NOW()),
  (3, 'RESULT',   'PARQUET', '{"defaultQueryRows":1000}',                   1, 1, NOW(), 1, NOW()),
  (4, 'APPROVAL', 'LOCAL',   '{}',                                          1, 1, NOW(), 1, NOW()),
  (5, 'RUNTIME',  'LOCAL',   '{}',                                          1, 1, NOW(), 1, NOW());

-- ===================== 3. 默认 AI Skills =====================
-- 三个出厂 skill(平台级,所有 workspace 共享)。admin 可在 UI 修改/禁用/删除。
-- definition 列只存 prompt 正文,结构化元数据(name/category/required_tools 等)都在独立列里。

INSERT IGNORE INTO `t_r_ai_skill` (`id`, `name`, `display_name`, `description`, `category`, `definition`, `required_tools`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES (1, 'debug_failed_execution', '调试失败的执行',
        '读取失败执行的日志与代码,定位问题并给出修复建议', 'DEBUG',
        'You are a data-engineering debugging expert.\n\nGiven a failed execution id and optionally the associated script code:\n\n1. Call `get_execution_logs` to read the error trace. Focus on the FIRST non-informational error.\n2. Call `get_script_content` to read the script that produced the error.\n3. If the error mentions a table/column not found, call `describe_table` to confirm the real schema.\n4. Diagnose the root cause. Be specific: "column `user_id` does not exist in `dwd_order`;\n   the actual column is `uid`" rather than "SQL error".\n5. Produce a minimal-diff fix. Show only the lines that need to change, with before/after.\n6. If you are not confident, explicitly ask the user for more context instead of guessing.\n\nNever "just run it again" as the answer — always explain what failed and why.',
        JSON_ARRAY('get_execution_logs', 'get_script_content', 'describe_table'),
        1, 1, NOW(), 1, NOW());

INSERT IGNORE INTO `t_r_ai_skill` (`id`, `name`, `display_name`, `description`, `category`, `definition`, `required_tools`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES (2, 'generate_task_code', '生成任务代码',
        '根据自然语言描述生成任务代码(SQL/Python/Shell/Flink/SeaTunnel 等)', 'CODE_GEN',
        'You are an expert data engineer writing task code for the Rudder platform.\n\nWorkflow:\n1. If the user describes a data query, ALWAYS call `describe_table` for every table\n   you reference before writing the SQL. Never hallucinate column names.\n2. Use `list_databases` / `list_tables` when the user is vague about source tables.\n3. For business-semantic questions (e.g. "daily active users"), search the workspace\n   wiki / data dictionary first — do NOT assume a definition.\n4. For existing similar work, call `search_scripts` to find references.\n5. Output the final code inside a fenced block with the correct dialect tag:\n   ```sql / ```python / ```bash / ```yaml (for Flink/SeaTunnel).\n6. Explain assumptions briefly; flag anything you''re uncertain about.\n\nQuality bar:\n- All tables / columns must exist according to metadata tools.\n- Joins must have explicit ON conditions with compatible types.\n- Date/time handling must match the dialect (e.g. DATE_TRUNC vs DATE_FORMAT).\n- Respect the workspace''s dialect: StarRocks / Trino / Spark / Hive / SeaTunnel / Python / Shell / Flink.',
        JSON_ARRAY('list_databases', 'list_tables', 'describe_table', 'search_scripts', 'sample_table'),
        1, 1, NOW(), 1, NOW());

INSERT IGNORE INTO `t_r_ai_skill` (`id`, `name`, `display_name`, `description`, `category`, `definition`, `required_tools`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES (3, 'optimize_sql', '优化 SQL',
        '分析现有 SQL 的性能瓶颈并给出重写建议', 'OPTIMIZE',
        'You are a SQL performance expert.\n\nGiven a script code:\n1. `get_script_content` to read the SQL.\n2. `describe_table` for each table referenced (to check indexes / partitions).\n3. Identify issues in this priority order:\n   - Full table scans on large tables\n   - Missing predicates on partition columns\n   - JOIN order / build side\n   - Sub-query vs JOIN / window function opportunities\n   - Implicit type casts disabling indexes\n   - `SELECT *` when not necessary\n4. Output three sections:\n   - **问题诊断**: bullet points, specific line references.\n   - **重写后的 SQL**: fenced block with the full optimized query.\n   - **预期改进**: one sentence, e.g. "避免 orders 全表扫描,按 dt 分区裁剪后数据量 ~5%。"\n\nKeep suggestions actionable. Don''t propose changes that depend on adding indexes\nunless the user can actually do that.',
        JSON_ARRAY('get_script_content', 'describe_table'),
        1, 1, NOW(), 1, NOW());

-- ===================== 4. 脱敏策略(内置) =====================
-- 规则表通过 code 引用,code 字符串跨环境稳定。

INSERT IGNORE INTO `t_r_redaction_strategy`
  (`code`, `name`, `description`, `executor_type`, `match_regex`, `replacement`, `keep_prefix`, `keep_suffix`, `mask_char`, `replace_value`, `hash_length`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES
  ('MASK_PHONE',      '手机号遮盖',      '保留前 3 后 4,中间遮盖',    'PARTIAL', NULL, NULL, 3, 4, '*', NULL, NULL, 1, 1, NOW(), 1, NOW()),
  ('MASK_EMAIL',      '邮箱遮盖',        '保留首字母和后 4 字符',      'PARTIAL', NULL, NULL, 1, 4, '*', NULL, NULL, 1, 1, NOW(), 1, NOW()),
  ('MASK_ID_CARD',    '身份证遮盖',      '保留前 3 后 4',              'PARTIAL', NULL, NULL, 3, 4, '*', NULL, NULL, 1, 1, NOW(), 1, NOW()),
  ('MASK_BANK_CARD',  '银行卡遮盖',      '保留前 4 后 4',              'PARTIAL', NULL, NULL, 4, 4, '*', NULL, NULL, 1, 1, NOW(), 1, NOW()),
  ('MASK_NAME_CN',    '中文姓名遮盖',    '仅保留姓氏',                'PARTIAL', NULL, NULL, 1, 0, '*', NULL, NULL, 1, 1, NOW(), 1, NOW()),
  ('REPLACE_REDACTED','整体替换为 ***',  '敏感值整体替换',            'REPLACE', NULL, NULL, NULL, NULL, NULL, '***', NULL, 1, 1, NOW(), 1, NOW()),
  ('HASH_SHORT',      'SHA256 短哈希',   '保 join 性,不泄真值',       'HASH',    NULL, NULL, NULL, NULL, NULL, NULL, 8,    1, 1, NOW(), 1, NOW()),
  ('REMOVE',          '置 null',         '完全清除',                  'REMOVE',  NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NOW(), 1, NOW());

-- ===================== 5. 默认脱敏规则 =====================
-- 平台内置的常见 PII 规则,启动即生效。admin 可禁用/修改/新增。
-- 代码里不再有任何硬编码 PII 正则,所有脱敏行为都走这两张表。

-- 显式 id 让 INSERT IGNORE 能基于 PK 冲突跳过；否则重启会无限重复插入(name 列没 UNIQUE 约束)。
INSERT IGNORE INTO `t_r_redaction_rule`
  (`id`, `name`, `description`, `type`, `pattern`, `strategy_code`, `priority`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES
  -- TEXT 规则:扫描日志 / AI 输出 / 字符串列内嵌 PII
  (1,  'text_phone',     '文本里的中国大陆手机号', 'TEXT', '(?<![0-9])1[3-9][0-9]{9}(?![0-9])',                              'MASK_PHONE',      10, 1, 1, NOW(), 1, NOW()),
  (2,  'text_email',     '文本里的邮箱',           'TEXT', '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}',                'MASK_EMAIL',      10, 1, 1, NOW(), 1, NOW()),
  (3,  'text_id_card',   '文本里的中国身份证',     'TEXT', '(?<![0-9])[1-9][0-9]{5}(19|20)[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9])', 'MASK_ID_CARD', 10, 1, 1, NOW(), 1, NOW()),
  (4,  'text_bank_card', '文本里的银行卡号(13-19 位连续数字)', 'TEXT', '(?<![0-9])[1-9][0-9]{12,18}(?![0-9])',                 'MASK_BANK_CARD',  20, 1, 1, NOW(), 1, NOW()),
  -- COLUMN 规则:按列名兜底(未打 tag 的列)
  (5,  'col_phone',      '列名含手机号语义',       'COLUMN', '^(phone|mobile|tel|phone_number|mobile_number|contact_phone)$', 'MASK_PHONE',     10, 1, 1, NOW(), 1, NOW()),
  (6,  'col_email',      '列名含邮箱语义',         'COLUMN', '^(email|mail|e_mail|email_address)$',                           'MASK_EMAIL',     10, 1, 1, NOW(), 1, NOW()),
  (7,  'col_id_card',    '列名含身份证语义',       'COLUMN', '^(id_card|idcard|identity_card|identity_number)$',              'MASK_ID_CARD',   10, 1, 1, NOW(), 1, NOW()),
  (8,  'col_bank_card',  '列名含银行卡语义',       'COLUMN', '^(bank_card|bank_account|card_number|card_no)$',                'MASK_BANK_CARD', 10, 1, 1, NOW(), 1, NOW()),
  (9,  'col_name_cn',    '列名含人名语义',         'COLUMN', '^(name|real_name|full_name|user_name|customer_name)$',          'MASK_NAME_CN',   20, 1, 1, NOW(), 1, NOW()),
  (10, 'col_credential', '列名含凭证语义',         'COLUMN', '^(password|passwd|secret|token|api_key|access_key|private_key)$','REPLACE_REDACTED', 10, 1, 1, NOW(), 1, NOW());


-- ===================== 6. 默认快捷入口 / 文档链接 =====================
-- icon 列存 SVG 的 base64 data URL,行较长是因为内联 SVG;不要改成 INSERT INTO,否则重启会因 PK 冲突挂 schema 初始化。
SET @SVG_DOC = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBzdGFuZGFsb25lPSJubyI/PjwhRE9DVFlQRSBzdmcgUFVCTElDICItLy9XM0MvL0RURCBTVkcgMS4xLy9FTiIgImh0dHA6Ly93d3cudzMub3JnL0dyYXBoaWNzL1NWRy8xLjEvRFREL3N2ZzExLmR0ZCI+PHN2ZyB0PSIxNzc4MTY5MzQ1OTkxIiBjbGFzcz0iaWNvbiIgdmlld0JveD0iMCAwIDEwMjQgMTAyNCIgdmVyc2lvbj0iMS4xIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHAtaWQ9Ijc2NjAiIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgeG1sbnM6eGxpbms9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkveGxpbmsiPjxwYXRoIGQ9Ik04MTUuMTA0IDY5LjYzMnEyNy42NDggMjUuNiA0NC4wMzIgNDIuNDk2dDI1LjA4OCAyOC42NzIgMTAuNzUyIDE5Ljk2OCAyLjA0OCAxNC4zMzZsMCAxNi4zODQtMTUxLjU1MiAwcS0xMC4yNCAwLTE3LjkyLTcuNjh0LTEyLjgtMTcuOTItNy42OC0yMC45OTItMi41Ni0xNi44OTZsMC0xMjYuOTc2IDMuMDcyIDBxOC4xOTIgMCAxNi44OTYgMi41NnQxOS45NjggOS43MjggMjguMTYgMjAuNDggNDIuNDk2IDM1Ljg0ek02NDAgMTI5LjAyNHEwIDIwLjQ4IDYuMTQ0IDQyLjQ5NnQxOS40NTYgNDAuOTYgMzMuNzkyIDMxLjIzMiA0OC4xMjggMTIuMjg4bDE0OS41MDQgMCAwIDU3Ny41MzZxMCAyOS42OTYtMTEuNzc2IDUzLjI0OHQtMzEuMjMyIDM5LjkzNi00My4wMDggMjUuNi00Ni4wOCA5LjIxNmwtNTAzLjgwOCAwcS0xOS40NTYgMC00Mi40OTYtMTEuMjY0dC00My4wMDgtMjkuNjk2LTMzLjI4LTQxLjk4NC0xMy4zMTItNDkuMTUybDAtNjk2LjMycTAtMjEuNTA0IDkuNzI4LTQ0LjU0NHQyNi42MjQtNDIuNDk2IDM4LjQtMzIuMjU2IDQ1LjA1Ni0xMi44bDM5MS4xNjggMCAwIDEyOHpNNzA0LjUxMiA3NjhxMjYuNjI0IDAgNDUuMDU2LTE4Ljk0NHQxOC40MzItNDUuNTY4LTE4LjQzMi00NS4wNTYtNDUuMDU2LTE4LjQzMmwtMzg0IDBxLTI2LjYyNCAwLTQ1LjA1NiAxOC40MzJ0LTE4LjQzMiA0NS4wNTYgMTguNDMyIDQ1LjU2OCA0NS4wNTYgMTguOTQ0bDM4NCAwek03NjggNDQ4LjUxMnEwLTI2LjYyNC0xOC40MzItNDUuNTY4dC00NS4wNTYtMTguOTQ0bC0zODQgMHEtMjYuNjI0IDAtNDUuMDU2IDE4Ljk0NHQtMTguNDMyIDQ1LjU2OCAxOC40MzIgNDUuMDU2IDQ1LjA1NiAxOC40MzJsMzg0IDBxMjYuNjI0IDAgNDUuMDU2LTE4LjQzMnQxOC40MzItNDUuMDU2eiIgcC1pZD0iNzY2MSI+PC9wYXRoPjwvc3ZnPg==';
SET @SVG_GITHUB = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNiAwQzcuMTYgMCAwIDcuMTYgMCAxNkMwIDIzLjA4IDQuNTggMjkuMDYgMTAuOTQgMzEuMThDMTEuNzQgMzEuMzIgMTIuMDQgMzAuODQgMTIuMDQgMzAuNDJDMTIuMDQgMzAuMDQgMTIuMDIgMjguNzggMTIuMDIgMjcuNDRDOCAyOC4xOCA2Ljk2IDI2LjQ2IDYuNjQgMjUuNTZDNi40NiAyNS4xIDUuNjggMjMuNjggNSAyMy4zQzQuNDQgMjMgMy42NCAyMi4yNiA0Ljk4IDIyLjI0QzYuMjQgMjIuMjIgNy4xNCAyMy40IDcuNDQgMjMuODhDOC44OCAyNi4zIDExLjE4IDI1LjYyIDEyLjEgMjUuMkMxMi4yNCAyNC4xNiAxMi42NiAyMy40NiAxMy4xMiAyMy4wNkM5LjU2IDIyLjY2IDUuODQgMjEuMjggNS44NCAxNS4xNkM1Ljg0IDEzLjQyIDYuNDYgMTEuOTggNy40OCAxMC44NkM3LjMyIDEwLjQ2IDYuNzYgOC44MiA3LjY0IDYuNjJDNy42NCA2LjYyIDguOTggNi4yIDEyLjA0IDguMjZDMTMuMzIgNy45IDE0LjY4IDcuNzIgMTYuMDQgNy43MkMxNy40IDcuNzIgMTguNzYgNy45IDIwLjA0IDguMjZDMjMuMSA2LjE4IDI0LjQ0IDYuNjIgMjQuNDQgNi42MkMyNS4zMiA4LjgyIDI0Ljc2IDEwLjQ2IDI0LjYgMTAuODZDMjUuNjIgMTEuOTggMjYuMjQgMTMuNCAyNi4yNCAxNS4xNkMyNi4yNCAyMS4zIDIyLjUgMjIuNjYgMTguOTQgMjMuMDZDMTkuNTIgMjMuNTYgMjAuMDIgMjQuNTIgMjAuMDIgMjYuMDJDMjAuMDIgMjguMTYgMjAgMjkuODggMjAgMzAuNDJDMjAgMzAuODQgMjAuMyAzMS4zNCAyMS4xIDMxLjE4QzI3LjQyIDI5LjA2IDMyIDIzLjA2IDMyIDE2QzMyIDcuMTYgMjQuODQgMCAxNiAwVjBaIiBmaWxsPSIjMjQyOTJFIi8+Cjwvc3ZnPgo=';
INSERT IGNORE INTO `t_r_quick_link`
  (`id`, `category`, `name`, `description`, `icon`, `url`, `target`, `sort_order`, `enabled`, `created_by`, `created_at`, `updated_by`, `updated_at`)
VALUES
  (1, 'DOC_LINK', 'API 文档',     'OpenAPI / Scalar UI', @SVG_DOC,    '/scalar',                      '_blank', 0, 1, 1, NOW(), 1, NOW()),
  (2, 'DOC_LINK', '项目仓库',     'GitHub 源码与 Wiki',   @SVG_GITHUB, 'https://github.com/zzih/rudder',        '_blank', 1, 1, 1, NOW(), 1, NOW()),
  (3, 'DOC_LINK', '问题反馈',     'GitHub Issues',        @SVG_GITHUB, 'https://github.com/zzih/rudder/issues', '_blank', 2, 1, 1, NOW(), 1, NOW());
