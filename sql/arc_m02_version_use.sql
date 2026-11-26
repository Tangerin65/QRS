-- M02 文件版本与档案利用增量脚本
-- 适用于已创建 arc_file、arc_audit_event 的数据库。
-- 首次部署时暂停附件写入并先备份相关表及附件文件。

CREATE TABLE IF NOT EXISTS arc_file_version (
    file_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    store_key VARCHAR(128) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_version_no INT DEFAULT NULL,
    PRIMARY KEY (file_id, version_no),
    KEY idx_arc_file_version_created (file_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案附件文件版本';

-- 通过元数据判断列是否存在，兼容 MySQL 5.7/8.0 的重复执行。
SET @m02_add_revoked = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema=DATABASE() AND table_name='arc_file' AND column_name='revoked'),
    'SELECT 1',
    'ALTER TABLE arc_file ADD COLUMN revoked TINYINT NOT NULL DEFAULT 0 COMMENT ''附件撤销标记：0有效，1已撤销''');
PREPARE m02_stmt FROM @m02_add_revoked;
EXECUTE m02_stmt;
DEALLOCATE PREPARE m02_stmt;

-- 将已存在附件迁移成 V1。以联合主键保证脚本重复执行时不会重复回填。
INSERT INTO arc_file_version
    (file_id, version_no, original_name, store_key, file_type, file_size, sha256, create_by, create_time)
SELECT f.file_id, 1, f.display_name, f.store_key, f.file_type, f.file_size, f.sha256, f.create_by, f.create_time
FROM arc_file f
LEFT JOIN arc_file_version v ON v.file_id=f.file_id AND v.version_no=1
WHERE v.file_id IS NULL;

-- M02 文件利用操作权限；权限需由管理员按角色授予。
INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M02 文件利用权限'
FROM (
    SELECT '预览档案文件' menu_name,23 order_num,'archiveFilePreview' perms UNION ALL
    SELECT '下载档案文件',24,'archiveFileDownload' UNION ALL
    SELECT '打印档案文件',25,'archiveFilePrint'
) action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms
);

-- 部署核对：每个现存附件都应有 V1，当前快照应与最新版本一致。
SELECT COUNT(*) AS missing_initial_version
FROM arc_file f
LEFT JOIN arc_file_version v ON v.file_id=f.file_id AND v.version_no=1
WHERE v.file_id IS NULL;

SELECT COUNT(*) AS current_version_mismatch
FROM arc_file f
JOIN arc_file_version v ON v.file_id=f.file_id
    AND v.version_no=(SELECT MAX(latest.version_no) FROM arc_file_version latest WHERE latest.file_id=f.file_id)
WHERE f.sha256<>v.sha256 OR f.store_key<>v.store_key;
