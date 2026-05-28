-- t_r_script.dir_id: NULL=根 → 0=根
-- 旧库需手动执行(schema.sql 用 CREATE TABLE IF NOT EXISTS,既有表不会自动 ALTER)
-- 配合代码:ScriptService.ROOT_DIR_ID=0L、uk_ws_dir_name (workspace_id, dir_id, name)

UPDATE t_r_script SET dir_id = 0 WHERE dir_id IS NULL;

ALTER TABLE t_r_script
    MODIFY COLUMN dir_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属目录ID, 0=根目录';

ALTER TABLE t_r_script DROP INDEX uk_ws_name;

ALTER TABLE t_r_script ADD UNIQUE KEY uk_ws_dir_name (workspace_id, dir_id, name);

ALTER TABLE t_r_script DROP INDEX idx_workspace;
