-- M01 档案保管期限快照字段迁移
-- 在已执行 arc_m01_schema.sql 的数据库运行

SET @has_retention_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'retention_name'
);
SET @retention_sql = IF(@has_retention_name = 0,
    'ALTER TABLE arc_archive ADD COLUMN retention_name VARCHAR(100) NOT NULL DEFAULT '''' AFTER retention_code',
    'SELECT 1');
PREPARE retention_stmt FROM @retention_sql;
EXECUTE retention_stmt;
DEALLOCATE PREPARE retention_stmt;

UPDATE arc_archive a
JOIN sys_dict_data d ON d.dict_type COLLATE utf8mb4_0900_ai_ci = _utf8mb4'arc_retention_period' COLLATE utf8mb4_0900_ai_ci
    AND d.dict_value COLLATE utf8mb4_0900_ai_ci = a.retention_code COLLATE utf8mb4_0900_ai_ci
SET a.retention_name = d.dict_label
WHERE a.retention_name = '';
