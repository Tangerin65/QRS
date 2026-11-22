-- M01 档案登记业务表
-- 在 records_framework_001.sql 和 arc_category.sql 执行后运行
-- 初始密级值采用 0公开、1内部、2秘密、3机密，后续可由 M13 字典统一
-- 保管期限从若依字典 arc_retention_period 读取，登记时保存名称快照

CREATE TABLE IF NOT EXISTS arc_archive_sequence (
    category_id BIGINT NOT NULL,
    forming_year SMALLINT NOT NULL,
    next_value INT NOT NULL DEFAULT 1,
    PRIMARY KEY (category_id, forming_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案编号序列';

CREATE TABLE IF NOT EXISTS arc_archive (
    archive_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_code VARCHAR(96) NOT NULL,
    title VARCHAR(200) NOT NULL,
    forming_date DATE NOT NULL,
    category_id BIGINT NOT NULL,
    category_code VARCHAR(32) NOT NULL,
    secret_level TINYINT NOT NULL DEFAULT 0,
    retention_code VARCHAR(32) NOT NULL DEFAULT '',
    retention_name VARCHAR(100) NOT NULL DEFAULT '',
    summary VARCHAR(1000) NOT NULL DEFAULT '',
    metadata_json JSON NOT NULL,
    archive_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    dept_id BIGINT NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (archive_id),
    UNIQUE KEY uk_arc_archive_code (archive_code),
    KEY idx_arc_archive_category (category_id, del_flag, archive_status),
    KEY idx_arc_archive_owner (create_by, del_flag, archive_status),
    KEY idx_arc_archive_dept (dept_id, del_flag, archive_status),
    KEY idx_arc_archive_forming_date (forming_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案登记信息';

CREATE TABLE IF NOT EXISTS arc_audit_event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_id BIGINT DEFAULT NULL,
    archive_code VARCHAR(96) DEFAULT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detail VARCHAR(500) NOT NULL DEFAULT '',
    PRIMARY KEY (event_id),
    KEY idx_arc_audit_archive_time (archive_id, event_time),
    KEY idx_arc_audit_actor_time (actor_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案业务审计事件';
