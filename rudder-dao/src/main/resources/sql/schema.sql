-- ============================================================
-- Rudder DDL Schema (auto-init on startup)
-- MySQL 8.0+  |  All statements are idempotent (IF NOT EXISTS)
-- ============================================================

-- ==================== Workspace Module ====================

CREATE TABLE IF NOT EXISTS `t_r_workspace` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64) NOT NULL              COMMENT '工作空间名称',
    `description` VARCHAR(512)                      COMMENT '描述',
    `created_by`  BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`  BIGINT                            COMMENT '更新人ID',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='工作空间';

CREATE TABLE IF NOT EXISTS `t_r_project` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `code`         BIGINT NOT NULL                   COMMENT '雪花ID业务标识',
    `workspace_id` BIGINT NOT NULL                   COMMENT '所属工作空间ID',
    `name`         VARCHAR(128) NOT NULL             COMMENT '项目名称',
    `description`  VARCHAR(512)                      COMMENT '描述',
    `params`       TEXT                              COMMENT '项目级参数 JSON (List<Property>)',
    `created_by`   BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`   BIGINT                            COMMENT '更新人ID',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_ws_name` (`workspace_id`, `name`),
    INDEX `idx_workspace` (`workspace_id`)
) ENGINE=InnoDB COMMENT='项目';

CREATE TABLE IF NOT EXISTS `t_r_user` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `username`       VARCHAR(64) NOT NULL              COMMENT '用户名',
    `password`       VARCHAR(256) DEFAULT ''            COMMENT '密码(BCrypt加密, SSO用户为空)',
    `email`          VARCHAR(128)                      COMMENT '邮箱',
    `avatar`         VARCHAR(512)                      COMMENT '头像URL',
    `is_super_admin` TINYINT DEFAULT 0                 COMMENT '是否超级管理员 0=否 1=是',
    `created_by`     BIGINT                            COMMENT '创建人ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人ID',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sso_provider`   VARCHAR(32)                       COMMENT 'SSO提供商: OIDC/LDAP',
    `sso_id`         VARCHAR(256)                      COMMENT 'SSO提供商侧用户唯一ID',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_sso` (`sso_provider`, `sso_id`)
) ENGINE=InnoDB COMMENT='用户';

CREATE TABLE IF NOT EXISTS `t_r_workspace_member` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workspace_id` BIGINT NOT NULL                   COMMENT '工作空间ID',
    `user_id`      BIGINT NOT NULL                   COMMENT '用户ID',
    `role`         VARCHAR(32) NOT NULL              COMMENT '角色: ADMIN/EDITOR/VIEWER',
    `created_by`   BIGINT                            COMMENT '创建人ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`   BIGINT                            COMMENT '更新人ID',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_ws_user` (`workspace_id`, `user_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='工作空间成员';

-- ==================== Authentication Module ====================

CREATE TABLE IF NOT EXISTS `t_r_auth_source` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL              COMMENT '显示名(登录页按钮文案)',
    `type`        VARCHAR(16)  NOT NULL              COMMENT '类型: OIDC / LDAP',
    `enabled`     TINYINT      NOT NULL DEFAULT 1    COMMENT '启用状态',
    `is_system`   TINYINT      NOT NULL DEFAULT 0    COMMENT '系统行(=1)不可删/不可禁',
    `priority`    INT          NOT NULL DEFAULT 0    COMMENT '登录页按钮排序,值大者靠前',
    `config_json` TEXT                               COMMENT 'config JSON(敏感字段已 AES 加密)',
    `created_by`  BIGINT                             COMMENT '创建人ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`  BIGINT                             COMMENT '更新人ID',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`),
    INDEX `idx_type_enabled` (`type`, `enabled`)
) ENGINE=InnoDB COMMENT='外部认证源(SSO): OIDC/LDAP,多源可同时启用;本地账号走 t_r_user 不进本表';

-- ==================== Datasource Module ====================

CREATE TABLE IF NOT EXISTS `t_r_datasource` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(64) NOT NULL              COMMENT '数据源名称',
    `datasource_type` VARCHAR(32) NOT NULL              COMMENT '数据源类型: MYSQL/HIVE/STARROCKS/TRINO/SPARK/FLINK',
    `host`          VARCHAR(256)                      COMMENT '主机地址',
    `port`          INT                               COMMENT '端口',
    `database_name` VARCHAR(128)                      COMMENT '数据库名',
    `params`        TEXT                              COMMENT 'JSON: 额外连接参数',
    `credential` TEXT                              COMMENT 'JSON: AES加密的用户名密码',
    `created_by`    BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                            COMMENT '更新人ID',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`),
    INDEX `idx_created_by` (`created_by`)
) ENGINE=InnoDB COMMENT='数据源(全局资源, 通过datasource_permission授权给工作空间)';

CREATE TABLE IF NOT EXISTS `t_r_datasource_permission` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `datasource_id` BIGINT NOT NULL                   COMMENT '数据源ID',
    `workspace_id`  BIGINT NOT NULL                   COMMENT '工作空间ID',
    `created_by`    BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                            COMMENT '更新人ID',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_ds_workspace` (`datasource_id`, `workspace_id`),
    INDEX `idx_workspace` (`workspace_id`),
    INDEX `idx_created_by` (`created_by`)
) ENGINE=InnoDB COMMENT='数据源授权(数据源与工作空间的关联)';

-- ==================== Script Module ====================

CREATE TABLE IF NOT EXISTS `t_r_script_dir` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workspace_id` BIGINT NOT NULL                   COMMENT '所属工作空间ID',
    `parent_id`    BIGINT DEFAULT NULL               COMMENT '父目录ID, NULL=根目录',
    `name`         VARCHAR(128) NOT NULL             COMMENT '目录名称',
    `sort_order`   INT DEFAULT 0                     COMMENT '排序序号',
    `created_by`   BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`   BIGINT                            COMMENT '更新人ID',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_workspace` (`workspace_id`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB COMMENT='脚本目录';

CREATE TABLE IF NOT EXISTS `t_r_script` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `code`            BIGINT NOT NULL                   COMMENT '雪花ID业务标识',
    `workspace_id`    BIGINT NOT NULL                   COMMENT '所属工作空间ID',
    `dir_id`          BIGINT                            COMMENT '所属目录ID',
    `name`            VARCHAR(128) NOT NULL             COMMENT '脚本名称',
    `task_type`       VARCHAR(32)                        COMMENT '任务类型: MYSQL/HIVE_SQL/PYTHON/SHELL等',
    `content`         LONGTEXT                          COMMENT '脚本内容(JSON: 与对应TaskParams结构一致)',
    `source_type`     VARCHAR(16) NOT NULL DEFAULT 'IDE' COMMENT '来源: IDE=IDE创建, TASK=工作流任务创建',
    `params`          TEXT DEFAULT NULL                  COMMENT '默认执行参数JSON(Map<String,String>)',
    `created_by`      BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人ID',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_ws_name` (`workspace_id`, `name`),
    INDEX `idx_workspace` (`workspace_id`),
    INDEX `idx_dir` (`dir_id`)
) ENGINE=InnoDB COMMENT='脚本';

CREATE TABLE IF NOT EXISTS `t_r_task_instance` (
    `id`                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`                  VARCHAR(255)                      COMMENT '实例名称(快照: baseName_yyyyMMdd_HHmmss_seq)',

    -- 任务定义快照
    `task_definition_code`  BIGINT DEFAULT NULL               COMMENT '关联任务定义code(雪花ID)',
    `script_code`           BIGINT DEFAULT NULL               COMMENT '关联脚本code',
    `task_type`             VARCHAR(32) NOT NULL              COMMENT '任务类型: MYSQL/HIVE_SQL/PYTHON/SHELL等',
    `content`               LONGTEXT                          COMMENT '执行时的TaskParams JSON快照',
    `params`                TEXT DEFAULT NULL                  COMMENT '执行参数JSON(varPool + inputParams合并)',

    -- 来源上下文
    `workspace_id`          BIGINT DEFAULT NULL               COMMENT '所属工作空间ID',
    `source_type`           VARCHAR(16) NOT NULL DEFAULT 'IDE' COMMENT '触发来源: IDE/TASK',
    `workflow_instance_id`  BIGINT DEFAULT NULL               COMMENT '关联工作流实例ID(工作流执行时)',

    -- 运行时状态
    `status`                VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `runtime_type`          VARCHAR(16)                       COMMENT '运行平台类型: LOCAL/ALIYUN/AWS',
    `app_id`                VARCHAR(200)                      COMMENT '外部应用ID(Flink Job ID等)',
    `tracking_url`          VARCHAR(500)                      COMMENT '外部监控链接(Flink UI等)',
    `execution_host`        VARCHAR(128)                      COMMENT '执行节点地址 host:port',
    `log_path`              VARCHAR(512)                      COMMENT '日志文件路径(Execution节点本地)',
    `started_at`            DATETIME                          COMMENT '开始时间',
    `finished_at`           DATETIME                          COMMENT '结束时间',
    `duration`              BIGINT                            COMMENT '执行耗时(毫秒), 冗余字段便于查询排序',
    `error_message`         TEXT                              COMMENT '错误信息',

    -- 结果
    `result_path`           VARCHAR(512)                      COMMENT '结果文件路径(FileStorage 相对路径, 经 RedactionService 脱敏后写入)',
    `row_count`             BIGINT                            COMMENT '返回行数',
    `var_pool`              TEXT DEFAULT NULL                  COMMENT '任务输出变量JSON, 工作流场景用于任务间参数传递',

    -- 审计
    `created_by`            BIGINT                            COMMENT '创建人ID',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`            BIGINT                            COMMENT '更新人ID',
    `updated_at`            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_task_definition_code` (`task_definition_code`),
    INDEX `idx_script` (`script_code`),
    INDEX `idx_workflow_instance` (`workflow_instance_id`),
    INDEX `idx_workflow_instance_status` (`workflow_instance_id`, `status`),
    INDEX `idx_workspace` (`workspace_id`),
    INDEX `idx_source_type` (`source_type`)
) ENGINE=InnoDB COMMENT='任务执行实例(IDE/工作流/直接执行 统一)';

-- ==================== Task Definition Module ====================

CREATE TABLE IF NOT EXISTS `t_r_task_definition` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `code`            BIGINT NOT NULL                   COMMENT '雪花ID业务标识',
    `workspace_id`    BIGINT DEFAULT NULL               COMMENT '所属工作空间ID(冗余)',
    `project_code`    BIGINT DEFAULT NULL               COMMENT '所属项目code(冗余)',
    `workflow_definition_code`   BIGINT NOT NULL                   COMMENT '所属工作流code',
    `name`            VARCHAR(128)                      COMMENT '任务名称/标签',
    `task_type`       VARCHAR(32) NOT NULL              COMMENT '任务类型: MYSQL/HIVE_SQL/CONDITION/SUB_WORKFLOW等',
    `script_code`     BIGINT DEFAULT NULL               COMMENT '关联脚本code(脚本类任务)',
    `description`     VARCHAR(512) DEFAULT NULL          COMMENT '任务描述',
    `input_params`    TEXT DEFAULT NULL                  COMMENT 'JSON: 输入参数映射',
    `output_params`   TEXT DEFAULT NULL                  COMMENT 'JSON: 输出参数映射',
    `priority`        VARCHAR(16) DEFAULT 'MEDIUM'       COMMENT '优先级: HIGH/MEDIUM/LOW',
    `delay_time`      INT DEFAULT 0                      COMMENT '延时执行(秒)',
    `retry_times`     INT DEFAULT 0                      COMMENT '重试次数',
    `retry_interval`  INT DEFAULT 30                     COMMENT '重试间隔(秒)',
    `timeout`         INT DEFAULT NULL                   COMMENT '超时时间(分钟)',
    `timeout_enabled` TINYINT DEFAULT 0                  COMMENT '是否启用超时告警',
    `timeout_notify_strategy` VARCHAR(64) DEFAULT NULL   COMMENT '超时通知策略JSON: ["WARN","FAILED"]',
    `is_enabled`      TINYINT DEFAULT 1                  COMMENT '是否启用 0=跳过 1=执行',
    `created_by`      BIGINT                            COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人ID',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`),
    INDEX `idx_workflow` (`workflow_definition_code`),
    UNIQUE KEY `uk_script_code` (`script_code`)
) ENGINE=InnoDB COMMENT='任务定义(工作流节点的任务配置)';

-- ==================== Workflow Module ====================

CREATE TABLE IF NOT EXISTS `t_r_workflow_definition` (
    `id`                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `code`                  BIGINT NOT NULL                   COMMENT '雪花ID业务标识',
    `workspace_id`          BIGINT NOT NULL                   COMMENT '所属工作空间ID',
    `project_code`          BIGINT NOT NULL                   COMMENT '所属项目code',
    `name`                  VARCHAR(128) NOT NULL             COMMENT '工作流名称',
    `description`           VARCHAR(512)                      COMMENT '描述',
    `dag_json`              LONGTEXT                          COMMENT 'DAG定义JSON: {nodes, edges}',
    `global_params`         TEXT                              COMMENT 'JSON: 全局参数定义',
    `published_version_id`  BIGINT DEFAULT NULL               COMMENT '已发布的版本ID',
    `created_by`            BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`            BIGINT                            COMMENT '更新人ID',
    `updated_at`            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`),
    INDEX `idx_workspace` (`workspace_id`),
    INDEX `idx_project` (`project_code`)
) ENGINE=InnoDB COMMENT='工作流定义';

CREATE TABLE IF NOT EXISTS `t_r_workflow_schedule` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workflow_definition_code`   BIGINT NOT NULL                   COMMENT '工作流code',
    `cron_expression` VARCHAR(64) NOT NULL              COMMENT 'Cron表达式',
    `start_time`      DATETIME DEFAULT NULL             COMMENT '调度生效开始时间',
    `end_time`        DATETIME DEFAULT NULL             COMMENT '调度生效结束时间',
    `timezone`        VARCHAR(64) DEFAULT 'Asia/Shanghai' COMMENT '时区',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '调度状态: ONLINE/OFFLINE',
    `created_by`      BIGINT                            COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人ID',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_workflow` (`workflow_definition_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='工作流调度配置(Cron定时)';

CREATE TABLE IF NOT EXISTS `t_r_publish_record` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `batch_code`   BIGINT NOT NULL                   COMMENT '批次code(雪花ID, 同一次发布共享)',
    `publish_type` VARCHAR(16) NOT NULL DEFAULT 'WORKFLOW' COMMENT '发布类型: WORKFLOW/PROJECT',
    `project_code` BIGINT NOT NULL                   COMMENT '项目code',
    `workflow_definition_code` BIGINT NOT NULL        COMMENT '工作流code',
    `version_no`   INT NOT NULL                      COMMENT '发布的版本号',
    `status`       VARCHAR(24) NOT NULL              COMMENT '状态: PENDING_APPROVAL/PUBLISHED/PUBLISH_FAILED',
    `remark`       VARCHAR(512)                      COMMENT '发布备注',
    `published_at` DATETIME                          COMMENT '实际发布时间',
    `created_by`   BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`   BIGINT                            COMMENT '更新人ID',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_batch` (`batch_code`),
    INDEX `idx_workflow` (`workflow_definition_code`),
    INDEX `idx_project` (`project_code`)
) ENGINE=InnoDB COMMENT='发布记录(审计性质, 不可删除)';

CREATE TABLE IF NOT EXISTS `t_r_workflow_instance` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workspace_id`   BIGINT DEFAULT NULL               COMMENT '所属工作空间ID(冗余)',
    `project_code`   BIGINT DEFAULT NULL               COMMENT '所属项目code(冗余)',
    `workflow_definition_code`  BIGINT NOT NULL                   COMMENT '工作流code',
    `name`           VARCHAR(128) DEFAULT NULL         COMMENT '实例名称, 如: daily_etl_20260327_143052_001',
    `version_id`     BIGINT DEFAULT NULL               COMMENT '运行的版本ID',
    `trigger_type`   VARCHAR(16) NOT NULL              COMMENT '触发方式: MANUAL/SCHEDULER',
    `status`         VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/FAILED/CANCELLED',
    `dag_snapshot`   LONGTEXT                          COMMENT '运行时DAG快照JSON',
    `runtime_params` TEXT                              COMMENT 'JSON: 运行时覆盖参数',
    `var_pool`       TEXT                              COMMENT 'JSON: 变量池(节点间传参)',
    `started_at`     DATETIME                          COMMENT '开始时间',
    `finished_at`    DATETIME                          COMMENT '结束时间',
    `owner_host`     VARCHAR(64) DEFAULT NULL          COMMENT 'runner 所在 Server RPC 地址(host:port), 用于多节点孤儿回收',
    `created_by`     BIGINT                            COMMENT '创建人ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人ID',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_workflow` (`workflow_definition_code`),
    INDEX `idx_owner_status` (`owner_host`, `status`)
) ENGINE=InnoDB COMMENT='工作流运行实例';


-- ==================== Version Module ====================

CREATE TABLE IF NOT EXISTS `t_r_version_record` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `resource_type` VARCHAR(32) NOT NULL              COMMENT '资源类型: WORKFLOW/SCRIPT',
    `resource_code` BIGINT NOT NULL                   COMMENT '资源code(雪花ID)',
    `version_no`    INT NOT NULL                      COMMENT '版本号(递增)',
    `storage_ref`   LONGTEXT                          COMMENT 'SPI provider 自描述的存储引用(LOCAL=内容本身, GIT=GitRef JSON)',
    `provider`      VARCHAR(16) NOT NULL              COMMENT '写入此版本所用 provider: LOCAL/GIT,load 时按它路由',
    `remark`        VARCHAR(512)                      COMMENT '版本备注',
    `created_by`    BIGINT                            COMMENT '创建人ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_resource` (`resource_type`, `resource_code`),
    UNIQUE KEY `uk_resource_version` (`resource_type`, `resource_code`, `version_no`)
) ENGINE=InnoDB COMMENT='版本记录(通用版本管理)';

-- ==================== Approval Module ====================

CREATE TABLE IF NOT EXISTS `t_r_approval_record` (
    `id`                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `channel`              VARCHAR(32) NOT NULL              COMMENT '审批渠道: LOCAL/LARK/DINGTALK/KISSFLOW',
    `external_approval_id` VARCHAR(128)                      COMMENT '外部审批系统单号',
    `resource_type`        VARCHAR(32) NOT NULL              COMMENT '资源类型: PROJECT_PUBLISH/WORKFLOW_PUBLISH/MCP_TOKEN/...',
    `resource_code`        BIGINT NOT NULL                   COMMENT '资源主键',
    `workspace_id`         BIGINT NOT NULL                   COMMENT '所属工作空间',
    `project_code`         BIGINT                            COMMENT '所属项目(可空)',
    `title`                VARCHAR(256)                      COMMENT '审批标题',
    `description`          VARCHAR(1024)                     COMMENT '审批描述',
    `submit_remark`        VARCHAR(512)                      COMMENT '申请理由',
    `status`               VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                                                             COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN/EXPIRED',
    `stage_chain`          VARCHAR(256) NOT NULL             COMMENT '阶段链 JSON 数组,提交时锁定',
    `current_stage`        VARCHAR(32) NOT NULL              COMMENT '当前所在阶段',
    `decision_rule`        VARCHAR(16) NOT NULL DEFAULT 'ANY_1'
                                                             COMMENT '决议规则: ANY_1/N_OF_M/ALL',
    `required_count`       INT NOT NULL DEFAULT 1            COMMENT 'N_OF_M 时的 N',
    `resolved_at`          DATETIME                          COMMENT '最终终结时间',
    `expires_at`           DATETIME                          COMMENT '超时时间(NULL=不超时)',
    `withdrawn_at`         DATETIME                          COMMENT '撤回时间',
    `withdrawn_reason`     VARCHAR(256)                      COMMENT '撤回理由',
    `created_by`           BIGINT NOT NULL                   COMMENT '申请人 user_id',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`           BIGINT,
    `updated_at`           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_external_approval_id` (`external_approval_id`),
    INDEX `idx_resource` (`resource_type`, `resource_code`),
    INDEX `idx_workspace_status` (`workspace_id`, `status`),
    INDEX `idx_status_expires` (`status`, `expires_at`),
    INDEX `idx_creator` (`created_by`, `status`)
) ENGINE=InnoDB COMMENT='审批主表';

CREATE TABLE IF NOT EXISTS `t_r_approval_decision` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `approval_id`      BIGINT NOT NULL                   COMMENT '关联 t_r_approval_record.id',
    `stage`            VARCHAR(32) NOT NULL              COMMENT '该决议所在阶段',
    `decider_user_id`  BIGINT NOT NULL                   COMMENT '决议者 user_id',
    `decider_username` VARCHAR(64) NOT NULL              COMMENT '决议者用户名(冗余,免 join)',
    `decision`         VARCHAR(16) NOT NULL              COMMENT 'APPROVE/REJECT',
    `decided_at`       DATETIME NOT NULL                 COMMENT '决议时间',
    `remark`           VARCHAR(512)                      COMMENT '决议意见',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_approval_stage_decider` (`approval_id`, `stage`, `decider_user_id`),
    INDEX `idx_approval` (`approval_id`),
    INDEX `idx_decider` (`decider_user_id`, `decided_at`)
) ENGINE=InnoDB COMMENT='审批决议(每条决议一行,多人留痕)';

-- 统一 SPI 配置表(File/Result/Runtime/Metadata/Version/Approval/Publish/Notification/
-- LLM/Embedding/Vector/Rerank 共表, type 区分; 每个 type 平台级单 active)。
CREATE TABLE IF NOT EXISTS `t_r_spi_config` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `type`            VARCHAR(32) NOT NULL              COMMENT 'SPI 类型: FILE/RESULT/RUNTIME/METADATA/VERSION/APPROVAL/PUBLISH/NOTIFICATION/LLM/EMBEDDING/VECTOR/RERANK',
    `provider`        VARCHAR(32) NOT NULL              COMMENT 'provider 标识(LOCAL/HDFS/CLAUDE/LARK 等)',
    `provider_params` JSON                              COMMENT 'provider 配置参数(JSON)',
    `enabled`         TINYINT DEFAULT 1                 COMMENT '是否启用 0=否 1=是',
    `created_by`      BIGINT                            COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人ID',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_type_provider` (`type`, `provider`),
    INDEX `idx_type_enabled` (`type`, `enabled`)
) ENGINE=InnoDB COMMENT='SPI 配置(per (type,provider) 一行;saveDetail 隐式 disableOthers 保证 per type 单 active)';

-- RAG 链路参数配置(单 row, 不是 SPI 选型 —— 字段值是 chunk size/topK/reranker 等参数)
CREATE TABLE IF NOT EXISTS `t_r_rag_pipeline_config` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `settings_json` JSON                              COMMENT 'RagPipelineSettings 序列化',
    `enabled`       TINYINT DEFAULT 1                 COMMENT '是否启用 0=否 1=是',
    `created_by`    BIGINT                            COMMENT '创建人ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                            COMMENT '更新人ID',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='RAG 链路参数配置(平台级单 row)';

-- ==================== Service Registry ====================

CREATE TABLE IF NOT EXISTS `t_r_service_registry` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `type`        VARCHAR(16) NOT NULL              COMMENT '服务类型: SERVER/EXECUTION',
    `host`        VARCHAR(128) NOT NULL             COMMENT '主机地址',
    `port`        INT NOT NULL                      COMMENT '端口',
    `start_time`  DATETIME NOT NULL                 COMMENT '启动时间',
    `heartbeat`   DATETIME NOT NULL                 COMMENT '最近心跳时间',
    `status`      VARCHAR(16) DEFAULT 'ONLINE'      COMMENT '状态: ONLINE/OFFLINE',
    `task_count`  INT DEFAULT 0                     COMMENT '当前执行中任务数(EXECUTION用)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_type_host_port` (`type`, `host`, `port`)
) ENGINE=InnoDB COMMENT='服务注册表(Server/Execution节点注册与心跳, 运维表物理删除)';

-- ==================== Audit Log ====================

CREATE TABLE IF NOT EXISTS `t_r_audit_log` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT NOT NULL                   COMMENT '操作用户ID',
    `username`       VARCHAR(64)                       COMMENT '操作用户名(操作时刻快照, 不随用户改名更新)',
    `module`         VARCHAR(32) NOT NULL              COMMENT '模块: WORKSPACE/SCRIPT/WORKFLOW/DATASOURCE',
    `action`         VARCHAR(32) NOT NULL              COMMENT '操作: CREATE/UPDATE/DELETE/EXECUTE',
    `resource_type`  VARCHAR(32)                       COMMENT '资源类型',
    `resource_code`  BIGINT                            COMMENT '资源code',
    `description`    VARCHAR(512)                      COMMENT '操作描述',
    `request_ip`     VARCHAR(64)                       COMMENT '请求IP',
    `request_method` VARCHAR(16)                       COMMENT 'HTTP method',
    `request_uri`    VARCHAR(256)                      COMMENT '请求路径',
    `request_params` TEXT                              COMMENT '请求参数 JSON',
    `status`         VARCHAR(16)                       COMMENT '执行结果: SUCCESS / FAILURE',
    `error_message`  VARCHAR(512)                      COMMENT '失败时异常摘要',
    `duration_ms`    BIGINT                            COMMENT '耗时(毫秒)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX `idx_user` (`user_id`),
    INDEX `idx_module_action` (`module`, `action`),
    INDEX `idx_module_resource` (`module`, `resource_code`),
    INDEX `idx_resource` (`resource_type`, `resource_code`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB COMMENT='操作审计日志(只追加不删除)';

-- ==================== MCP Platform ====================
-- Personal Access Token + 阶段级 scope grant + 审批关联
-- 详见 docs/mcp-platform.md §3.2

CREATE TABLE IF NOT EXISTS `t_r_mcp_token` (
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT NOT NULL                   COMMENT '归属用户',
    `workspace_id`      BIGINT NOT NULL                   COMMENT '绑定的唯一工作空间(token 所有调用限定此 ws)',
    `name`              VARCHAR(64) NOT NULL              COMMENT 'token 名(用户起的,如 Claude Desktop on Mac)',
    `description`       VARCHAR(512)                      COMMENT '用途说明',
    `token_prefix`      VARCHAR(16) NOT NULL              COMMENT 'rdr_pat_xxxx 前 12 字符,索引查询',
    `token_hash`        VARCHAR(128) NOT NULL             COMMENT 'bcrypt 全文 hash (cost=10)',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                                                          COMMENT 'ACTIVE / REVOKED / EXPIRED',
    `expires_at`        DATETIME NOT NULL                 COMMENT '过期时间(最大 1 年)',
    `last_used_at`      DATETIME                          COMMENT '最近使用时间(异步更新)',
    `last_used_ip`      VARCHAR(64)                       COMMENT '最近调用 IP',
    `revoked_at`        DATETIME                          COMMENT '撤销时间(软删,保留审计)',
    `revoked_reason`    VARCHAR(256)                      COMMENT 'USER_REVOKE/ROLE_DOWNGRADE/EXPIRED/ADMIN_REVOKE',
    `created_by`        BIGINT NOT NULL                   COMMENT '创建人 user_id',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`        BIGINT,
    `updated_at`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_token_prefix` (`token_prefix`),
    INDEX `idx_user_status` (`user_id`, `status`),
    INDEX `idx_workspace_status` (`workspace_id`, `status`),
    INDEX `idx_expires` (`expires_at`)
) ENGINE=InnoDB COMMENT='MCP PAT 主表';

CREATE TABLE IF NOT EXISTS `t_r_mcp_token_scope_grant` (
    `id`                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `token_id`              BIGINT NOT NULL                   COMMENT '关联 t_r_mcp_token.id',
    `capability_id`         VARCHAR(64) NOT NULL              COMMENT 'capability 标识(如 script.author)',
    `rw_class`              VARCHAR(8) NOT NULL               COMMENT 'READ / WRITE',
    `status`                VARCHAR(24) NOT NULL              COMMENT 'PENDING_APPROVAL / ACTIVE / REJECTED / WITHDRAWN / REVOKED',
    `approval_id`           BIGINT                            COMMENT '关联 t_r_approval_record.id (仅 WRITE)',
    `activated_at`          DATETIME                          COMMENT '激活时间(READ 创建即填,WRITE 审批通过后填)',
    `activated_by_user_id`  BIGINT                            COMMENT '审批通过的 owner(READ 为 NULL)',
    `rejected_at`           DATETIME                          COMMENT '拒绝时间',
    `rejected_by_user_id`   BIGINT                            COMMENT '拒绝的 owner',
    `rejected_reason`       VARCHAR(256)                      COMMENT '拒绝理由',
    `revoked_at`            DATETIME                          COMMENT '撤销时间',
    `revoked_reason`        VARCHAR(64)                       COMMENT 'ROLE_DOWNGRADE/TOKEN_REVOKED/USER_WITHDRAW',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_token` (`token_id`),
    INDEX `idx_token_status` (`token_id`, `status`),
    INDEX `idx_approval` (`approval_id`)
) ENGINE=InnoDB COMMENT='MCP token scope 授权(每 capability 一行,WRITE 走审批)';

-- ==================== AI Platform ====================
-- 17 张表: 核心对话 + provider/budget + agent/skill/mcp + context + rag + eval/feedback
-- 外加平台级脱敏 2 张表(见 Data Redaction 段)

-- -------------------- Core: Session / Message --------------------

CREATE TABLE IF NOT EXISTS `t_r_ai_session` (
    `id`                       BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workspace_id`             BIGINT NOT NULL                   COMMENT '所属工作空间ID',
    `title`                    VARCHAR(256) NOT NULL             COMMENT '会话标题',
    `mode`                     VARCHAR(16) NOT NULL              COMMENT 'CHAT | AGENT',
    `model_snapshot`           VARCHAR(64)                       COMMENT '首条 turn 使用的模型',
    `system_prompt_override`   TEXT                              COMMENT '可选,覆盖默认 system prompt',
    `total_prompt_tokens`      BIGINT NOT NULL DEFAULT 0         COMMENT '累计 prompt token',
    `total_completion_tokens`  BIGINT NOT NULL DEFAULT 0         COMMENT '累计 completion token',
    `total_cost_cents`         BIGINT NOT NULL DEFAULT 0         COMMENT '累计成本(分)',
    `created_by`               BIGINT NOT NULL                   COMMENT '创建人ID',
    `created_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`               BIGINT                            COMMENT '更新人ID',
    `updated_at`               DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_workspace_user` (`workspace_id`, `created_by`)
) ENGINE=InnoDB COMMENT='AI 会话';

CREATE TABLE IF NOT EXISTS `t_r_ai_message` (
    `id`                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `session_id`         BIGINT NOT NULL                   COMMENT '所属会话ID',
    `turn_id`            CHAR(26) NOT NULL                 COMMENT 'ULID,一轮内消息共享',
    `role`               VARCHAR(16) NOT NULL              COMMENT 'user|assistant|tool_call|tool_result|system',
    `status`             VARCHAR(16)                       COMMENT 'PENDING|STREAMING|DONE|CANCELLED|FAILED(assistant 适用)',
    `content`            LONGTEXT                          COMMENT 'user/assistant 文本内容',
    `error_message`      VARCHAR(1024)                     COMMENT '失败时的错误摘要',
    `tool_call_id`       VARCHAR(64)                       COMMENT 'provider 返回的 tool_call id,配对 call/result',
    `tool_name`          VARCHAR(64)                       COMMENT '工具名',
    `tool_source`        VARCHAR(16)                       COMMENT 'NATIVE|SKILL|MCP',
    `tool_input`         JSON                              COMMENT '工具输入参数',
    `tool_output`        LONGTEXT                          COMMENT '工具输出结果',
    `tool_success`       TINYINT                           COMMENT '工具执行是否成功',
    `tool_latency_ms`    INT                               COMMENT '工具执行耗时',
    `model`              VARCHAR(64)                       COMMENT '生成时使用的模型',
    `prompt_tokens`      INT                               COMMENT 'prompt token 数',
    `completion_tokens`  INT                               COMMENT 'completion token 数',
    `cost_cents`         INT                               COMMENT '本消息成本(分)',
    `latency_ms`         INT                               COMMENT '生成耗时',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_session_created` (`session_id`, `created_at`),
    INDEX `idx_turn` (`turn_id`)
) ENGINE=InnoDB COMMENT='AI 消息(含 tool_call / tool_result)';

-- -------------------- Agent / Skill / MCP --------------------
-- 使用量直接从 t_r_ai_message.prompt_tokens/completion_tokens/cost_cents 聚合,不单独维护预算表。

-- 方言 prompt 的用户覆盖层:classpath:ai-prompts/dialects/{TaskType}.md 是出厂默认,
-- admin 在此处改动后优先生效;删除记录即回退到 classpath 版本。
CREATE TABLE IF NOT EXISTS `t_r_ai_dialect` (
    `id`         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `task_type`  VARCHAR(32) NOT NULL              COMMENT 'TaskType 枚举名,如 MYSQL / PYTHON / SEATUNNEL',
    `content`    LONGTEXT NOT NULL                 COMMENT '覆盖的方言 prompt 正文',
    `enabled`    TINYINT NOT NULL DEFAULT 1        COMMENT '1=启用此覆盖; 0=忽略此覆盖,走 classpath 默认',
    `created_by` BIGINT                            COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` BIGINT                            COMMENT '更新人ID',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_task_type` (`task_type`)
) ENGINE=InnoDB COMMENT='方言 prompt 覆盖层(classpath md 之上)';

CREATE TABLE IF NOT EXISTS `t_r_ai_skill` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(64) NOT NULL              COMMENT '唯一 slug,如 "optimize_sql"',
    `display_name`   VARCHAR(128) NOT NULL             COMMENT '展示名',
    `description`    VARCHAR(1024)                     COMMENT '说明',
    `category`       VARCHAR(32)                       COMMENT 'SkillCategory 枚举:CODE_GEN|DEBUG|OPTIMIZE|EXPLAIN|REVIEW|ANALYZE|DATA_DISCOVERY|OPS|OTHER',
    `definition`     LONGTEXT NOT NULL                 COMMENT 'prompt 正文(直接作为 system prompt)',
    `input_schema`   JSON                              COMMENT 'JSON Schema,参数校验用',
    `required_tools` JSON                              COMMENT '依赖的工具名列表',
    `model_override` VARCHAR(128)                      COMMENT '可选,该 skill 强制用某模型',
    `enabled`        TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `created_by`     BIGINT                            COMMENT '创建人ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人ID',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB COMMENT='AI skills库(纯定义;工作区可见性在 t_r_ai_tool_config 配置)';

CREATE TABLE IF NOT EXISTS `t_r_ai_mcp_server` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`            VARCHAR(64) NOT NULL              COMMENT '如 "slack-prod",server 级唯一',
    `transport`       VARCHAR(16) NOT NULL              COMMENT 'STDIO|HTTP_SSE',
    `command`         VARCHAR(1024)                     COMMENT 'stdio 时的启动命令',
    `url`             VARCHAR(512)                      COMMENT 'http-sse 时的地址',
    `env`             JSON                              COMMENT '启动环境变量',
    `credentials`     JSON                              COMMENT '加密存储',
    `tool_allowlist`  JSON                              COMMENT 'server 侧第一层过滤:["tool1","tool2"];null=暴露 server 全部',
    `health_status`   VARCHAR(16)                       COMMENT 'UP|DOWN|UNKNOWN',
    `last_health_at`  DATETIME                          COMMENT '最近一次健康检查时间',
    `enabled`         TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `created_by`      BIGINT                            COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人ID',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='MCP server 配置(server 凭证/启动命令;工作区可见性在 t_r_ai_tool_config 配置)';

CREATE TABLE IF NOT EXISTS `t_r_ai_tool_config` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `tool_name`       VARCHAR(128) NOT NULL             COMMENT 'NATIVE 裸名 / mcp:server.tool / skill__xxx',
    `workspace_ids`   JSON                              COMMENT 'null=所有工作区生效;[1,3]=仅这些工作区生效',
    `min_role`        VARCHAR(16)                       COMMENT 'null=走 PermissionGate 代码默认',
    `require_confirm` TINYINT                           COMMENT 'null=走 PermissionGate 代码默认',
    `read_only`       TINYINT                           COMMENT 'null=走 PermissionGate 代码默认',
    `enabled`         TINYINT NOT NULL DEFAULT 1        COMMENT 'false=在 workspace_ids 的工作区隐藏此 tool',
    `created_by`      BIGINT                            COMMENT '创建人',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tool_name` (`tool_name`)
) ENGINE=InnoDB COMMENT='AI 工具配置(可见性 + 权限规则;每 tool 最多一行)';

-- -------------------- Context --------------------

CREATE TABLE IF NOT EXISTS `t_r_ai_pinned_table` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `scope`           VARCHAR(16) NOT NULL              COMMENT 'USER|WORKSPACE',
    `scope_id`        BIGINT NOT NULL                   COMMENT 'userId / workspaceId',
    `datasource_id`   BIGINT NOT NULL                   COMMENT '数据源ID',
    `catalog_name`    VARCHAR(128)                      COMMENT 'catalog(三层引擎如 Trino;两层引擎为 null)',
    `database_name`   VARCHAR(128)                      COMMENT '库名',
    `table_name`      VARCHAR(128) NOT NULL             COMMENT '表名',
    `note`            VARCHAR(512)                      COMMENT '用户备注(作为 AI context)',
    `created_by`      BIGINT                            COMMENT '创建人',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT                            COMMENT '更新人',
    `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_scope` (`scope`, `scope_id`)
) ENGINE=InnoDB COMMENT='用户/工作区 pin 的表';

CREATE TABLE IF NOT EXISTS `t_r_ai_context_profile` (
    `id`                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `scope`                VARCHAR(16) NOT NULL              COMMENT 'WORKSPACE|SESSION',
    `scope_id`             BIGINT NOT NULL                   COMMENT 'workspaceId / sessionId',
    `inject_schema_level`  VARCHAR(16) NOT NULL DEFAULT 'TABLES' COMMENT 'NONE|TABLES|FULL',
    `max_schema_tables`    INT NOT NULL DEFAULT 50           COMMENT '注入表数上限',
    `inject_open_script`   TINYINT NOT NULL DEFAULT 1        COMMENT '是否注入当前打开脚本',
    `inject_selection`     TINYINT NOT NULL DEFAULT 1        COMMENT '是否注入选中片段',
    `inject_wiki_rag`      TINYINT NOT NULL DEFAULT 1        COMMENT '是否注入 wiki RAG 召回',
    `inject_history_last`  INT NOT NULL DEFAULT 10           COMMENT '注入最近 N 条历史',
    `created_by`           BIGINT                            COMMENT '创建人',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`           BIGINT                            COMMENT '更新人',
    `updated_at`           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_scope` (`scope`, `scope_id`)
) ENGINE=InnoDB COMMENT='上下文注入策略';

-- -------------------- RAG --------------------

CREATE TABLE IF NOT EXISTS `t_r_ai_metadata_sync_config` (
    `id`                     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `datasource_id`          BIGINT NOT NULL                   COMMENT '数据源 ID(唯一)',
    `enabled`                TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `schedule_cron`          VARCHAR(64)                       COMMENT 'Spring cron,空=仅手动',
    `include_catalogs`       JSON                              COMMENT '白名单 catalog(仅 3 层引擎,空=全部)',
    `include_databases`      JSON                              COMMENT '白名单库(空=全部),3 层引擎含 catalog 前缀 "catalog.db"',
    `include_tables`         JSON                              COMMENT '白名单表(空=上层全部),含 db 前缀 "db.table" 或 "catalog.db.table"',
    `exclude_tables`         JSON                              COMMENT '排除表关键字(不区分大小写 substring,; 分隔)',
    `max_columns_per_table`  INT NOT NULL DEFAULT 50           COMMENT '每表最大列数',
    `access_paths`           JSON                              COMMENT '跨引擎访问路径映射 [{engine,template}]',
    `last_sync_at`           DATETIME                          COMMENT '最近同步时间',
    `last_sync_status`       VARCHAR(32)                       COMMENT 'SUCCESS|FAILED|RUNNING',
    `last_sync_message`      VARCHAR(1024)                     COMMENT '最近同步信息',
    `created_by`             BIGINT                            COMMENT '创建人',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`             BIGINT                            COMMENT '更新人',
    `updated_at`             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_datasource` (`datasource_id`)
) ENGINE=InnoDB COMMENT='AI 元数据同步配置(每数据源一条)';

CREATE TABLE IF NOT EXISTS `t_r_ai_document` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `workspace_ids` JSON                              COMMENT 'null=所有工作区可见(平台级);[1,3]=仅这些工作区。SCHEMA 强制 null;SCRIPT 必须非空数组。跟 t_r_ai_tool_config.workspace_ids 同模型',
    `doc_type`      VARCHAR(32) NOT NULL              COMMENT 'WIKI|SCRIPT|SCHEMA|METRIC_DEF|RUNBOOK',
    `engine_type`   VARCHAR(32)                       COMMENT 'SCHEMA 类必填:HIVE|STARROCKS|TRINO|MYSQL|...',
    `source_ref`    VARCHAR(256)                      COMMENT '增量同步唯一标识(如 metadata:{ds}:{db}.{table});手写 doc 为 null',
    `title`         VARCHAR(512) NOT NULL             COMMENT '标题',
    `content`       LONGTEXT NOT NULL                 COMMENT '原文',
    `description`   VARCHAR(1024)                     COMMENT '人读备注(更新原因/负责人/来源链接等),不参与 RAG',
    `content_hash`  CHAR(64)                          COMMENT 'SHA-256,判断是否需要重索引',
    `indexed_at`    DATETIME                          COMMENT '最近一次向量化时间;null=未索引',
    `created_by`    BIGINT                            COMMENT '创建人',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                            COMMENT '更新人',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FULLTEXT KEY `ft_content` (`title`, `content`) WITH PARSER ngram,
    INDEX `idx_type` (`doc_type`),
    INDEX `idx_engine` (`engine_type`),
    UNIQUE KEY `uk_source_ref` (`source_ref`)
) ENGINE=InnoDB COMMENT='AI 文档原文(含 FULLTEXT 降级索引;可见性走 workspace_ids JSON 数组)';

CREATE TABLE IF NOT EXISTS `t_r_ai_document_embedding` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `document_id`      BIGINT NOT NULL                   COMMENT '关联 t_r_ai_document',
    `chunk_index`      INT NOT NULL DEFAULT 0            COMMENT 'chunk 序号',
    `chunk_text`       TEXT NOT NULL                     COMMENT '该 chunk 原文(审计)',
    `qdrant_point_id`  CHAR(36)                          COMMENT 'Qdrant point uuid',
    `embedding_model`  VARCHAR(64)                       COMMENT '用的 embedding 模型',
    `embedding_dim`    INT                               COMMENT '向量维度',
    `created_by`       BIGINT                            COMMENT '创建人',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`       BIGINT                            COMMENT '更新人',
    `updated_at`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_document` (`document_id`),
    UNIQUE KEY `uk_qdrant` (`qdrant_point_id`)
) ENGINE=InnoDB COMMENT='文档向量索引元信息(向量在 Qdrant)';

-- -------------------- Evaluation & Feedback --------------------

CREATE TABLE IF NOT EXISTS `t_r_ai_eval_case` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `category`      VARCHAR(32) NOT NULL              COMMENT 'SQL_GEN|OPTIMIZE|DEBUG|EXPLAIN|...',
    `task_type`     VARCHAR(32)                       COMMENT 'STARROCKS_SQL|PYTHON|FLINK|...',
    `difficulty`    VARCHAR(16)                       COMMENT 'EASY|MEDIUM|HARD',
    `mode`          VARCHAR(16) NOT NULL DEFAULT 'AGENT' COMMENT 'AGENT|CHAT — 执行器模式',
    `datasource_id` BIGINT                            COMMENT '执行语境:在哪个数据源上跑(注入 schema / dialect)',
    `engine_type`   VARCHAR(32)                       COMMENT '引擎类型冗余(ds 删除后仍可知语境)',
    `workspace_id`  BIGINT                            COMMENT '归属工作空间;null=默认',
    `prompt`        TEXT NOT NULL                     COMMENT '测试 prompt(用户消息内容)',
    `context_json`  JSON                              COMMENT '额外上下文:selection/pinnedTables/scriptCode 等',
    `expected_json` JSON                              COMMENT '断言 spec(文本/工具/性能)',
    `active`        TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `created_by`    BIGINT                            COMMENT '创建人ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                            COMMENT '更新人',
    `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_category_active` (`category`, `active`)
) ENGINE=InnoDB COMMENT='AI 评测用例';

CREATE TABLE IF NOT EXISTS `t_r_ai_eval_run` (
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `batch_id`          CHAR(26) NOT NULL                 COMMENT '评测批次 ULID',
    `case_id`           BIGINT NOT NULL                   COMMENT '关联 t_r_ai_eval_case',
    `provider`          VARCHAR(32)                       COMMENT 'AI provider(CLAUDE/OPENAI/...)',
    `model`             VARCHAR(64)                       COMMENT '用的模型',
    `passed`            TINYINT NOT NULL                  COMMENT '是否通过所有断言',
    `score`             DECIMAL(5,2)                      COMMENT '得分(暂时二值 100/0)',
    `final_text`        LONGTEXT                          COMMENT 'LLM 最终回复全文',
    `tool_calls_json`   JSON                              COMMENT '工具调用序列 [{name,input,output,success,latencyMs}]',
    `fail_reasons_json` JSON                              COMMENT '失败原因列表;passed=1 时为空数组',
    `latency_ms`        INT                               COMMENT '总耗时',
    `prompt_tokens`     INT                               COMMENT 'Prompt token 数',
    `completion_tokens` INT                               COMMENT '输出 token 数',
    `created_by`        BIGINT                            COMMENT '创建人',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`        BIGINT                            COMMENT '更新人',
    `updated_at`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_batch` (`batch_id`),
    INDEX `idx_case` (`case_id`, `created_at`)
) ENGINE=InnoDB COMMENT='AI 评测执行结果';

CREATE TABLE IF NOT EXISTS `t_r_ai_feedback` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `message_id`     BIGINT NOT NULL                   COMMENT '关联 t_r_ai_message',
    `user_id`        BIGINT NOT NULL                   COMMENT '反馈用户',
    `signal`         VARCHAR(16) NOT NULL              COMMENT 'THUMBS_UP|THUMBS_DOWN|EXECUTED_OK|EXECUTED_FAIL|EDITED|CANCELLED',
    `comment`        VARCHAR(1024)                     COMMENT '用户补充评论',
    `auto_generated` TINYINT NOT NULL DEFAULT 0        COMMENT '1=系统自动打的信号',
    `created_by`     BIGINT                            COMMENT '创建人(= user_id)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_message` (`message_id`)
) ENGINE=InnoDB COMMENT='AI 反馈信号';

-- ==================== Data Redaction (Platform Level) ====================

-- 脱敏策略表:定义"怎么脱",数据驱动,admin 可新增/编辑
CREATE TABLE IF NOT EXISTS `t_r_redaction_strategy` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `code`           VARCHAR(32) NOT NULL UNIQUE       COMMENT '策略编码,规则表通过 code 引用(跨环境稳定)',
    `name`           VARCHAR(128) NOT NULL             COMMENT '显示名',
    `description`    VARCHAR(512)                      COMMENT '说明',
    `executor_type`  VARCHAR(32) NOT NULL              COMMENT 'REGEX_REPLACE|PARTIAL|REPLACE|HASH|REMOVE',
    -- REGEX_REPLACE 用
    `match_regex`    VARCHAR(512)                      COMMENT 'type=REGEX_REPLACE:匹配正则',
    `replacement`    VARCHAR(512)                      COMMENT 'type=REGEX_REPLACE:替换模板,可带反向引用 $1 $2',
    -- PARTIAL 用
    `keep_prefix`    INT                               COMMENT 'type=PARTIAL:保留前 N 字符',
    `keep_suffix`    INT                               COMMENT 'type=PARTIAL:保留后 M 字符',
    `mask_char`      VARCHAR(4)                        COMMENT 'type=PARTIAL:遮盖字符,默认 *',
    -- REPLACE 用
    `replace_value`  VARCHAR(256)                      COMMENT 'type=REPLACE:整体替换值',
    -- HASH 用
    `hash_length`    INT                               COMMENT 'type=HASH:SHA256 截取前 N 位',
    `enabled`        TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `created_by`     BIGINT                            COMMENT '创建人ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人ID',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='脱敏策略(怎么脱)';

-- 脱敏规则表:定义"匹配什么",每条规则引用一个策略
CREATE TABLE IF NOT EXISTS `t_r_redaction_rule` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(128) NOT NULL             COMMENT '规则名',
    `description`    VARCHAR(512)                      COMMENT '说明',
    `type`           VARCHAR(16) NOT NULL              COMMENT 'TAG|COLUMN|TEXT',
    `pattern`        VARCHAR(1024) NOT NULL            COMMENT 'TAG:tag 字符串/通配/正则;COLUMN:列名正则;TEXT:文本正则',
    `strategy_code`  VARCHAR(32) NOT NULL              COMMENT '引用 t_r_redaction_strategy.code',
    `priority`       INT NOT NULL DEFAULT 100          COMMENT '同类型内优先级,小者先',
    `enabled`        TINYINT NOT NULL DEFAULT 1        COMMENT '是否启用',
    `created_by`     BIGINT                            COMMENT '创建人ID',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     BIGINT                            COMMENT '更新人ID',
    `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_type_enabled` (`type`, `enabled`)
) ENGINE=InnoDB COMMENT='脱敏规则(匹配什么)';

-- ==================== Quick Link ====================

CREATE TABLE IF NOT EXISTS `t_r_quick_link` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `category`     VARCHAR(32) NOT NULL              COMMENT '分类: QUICK_ENTRY/DOC_LINK',
    `name`         VARCHAR(64) NOT NULL              COMMENT '显示名',
    `description`  VARCHAR(255)                      COMMENT '副标题/描述',
    `icon`         TEXT                              COMMENT 'SVG icon, data URL: data:image/svg+xml;base64,...',
    `url`          VARCHAR(512) NOT NULL             COMMENT '跳转地址',
    `target`       VARCHAR(16) DEFAULT '_blank'      COMMENT '_blank 新开 / _self 当前页',
    `sort_order`   INT DEFAULT 0                     COMMENT '排序值,小在前',
    `enabled`      TINYINT DEFAULT 1                 COMMENT '是否启用 0=否 1=是',
    `created_by`   BIGINT                            COMMENT '创建人ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`   BIGINT                            COMMENT '更新人ID',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_category_sort` (`category`, `sort_order`)
) ENGINE=InnoDB COMMENT='首页快捷入口/文档链接（平台级共享）';
