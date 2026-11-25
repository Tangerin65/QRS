-- M02 档案附件上传
-- 在 arc_m01_schema.sql 执行后运行
-- 相对路径仅用于保留目录结构，实际文件采用唯一存储键
CREATE TABLE IF NOT EXISTS arc_file (
    file_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_id BIGINT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    store_key VARCHAR(128) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_arc_file_store_key (store_key),
    KEY idx_arc_file_archive (archive_id, file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案附件';
