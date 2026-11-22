-- 在已创建 arc_file 的档案库执行，后台自动补录已有附件正文。
CREATE TABLE IF NOT EXISTS arc_file_text (
    file_id BIGINT NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    retry_after DATETIME DEFAULT NULL,
    PRIMARY KEY (file_id),
    CONSTRAINT fk_arc_file_text_file FOREIGN KEY (file_id) REFERENCES arc_file(file_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
