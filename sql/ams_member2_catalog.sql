-- M03 分类基础增量。在开发库执行；重复执行不会清空数据。
CREATE TABLE IF NOT EXISTS ams_edit_lock (
    id INT PRIMARY KEY
) ENGINE=InnoDB;
INSERT IGNORE INTO ams_edit_lock(id) VALUES(1);

CREATE TABLE IF NOT EXISTS ams_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    parent_id BIGINT NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'active',
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NOT NULL DEFAULT '',
    revision INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ams_catalog_parent FOREIGN KEY (parent_id) REFERENCES ams_catalog(id),
    INDEX idx_ams_catalog_kind_status(kind,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
