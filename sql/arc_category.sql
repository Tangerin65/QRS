CREATE TABLE IF NOT EXISTS arc_category_edit_lock (
    id TINYINT PRIMARY KEY
) ENGINE=InnoDB;
INSERT IGNORE INTO arc_category_edit_lock(id) VALUES (1);

CREATE TABLE IF NOT EXISTS arc_category (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(32) NOT NULL,
    category_name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    parent_id BIGINT NULL,
    category_path VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 启用，1 停用',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NOT NULL DEFAULT '',
    revision INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arc_category_code (category_code),
    KEY idx_arc_category_parent (parent_id),
    KEY idx_arc_category_path (category_path),
    CONSTRAINT fk_arc_category_parent FOREIGN KEY (parent_id) REFERENCES arc_category(category_id),
    CONSTRAINT ck_arc_category_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
