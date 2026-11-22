-- M04 元数据模板
CREATE TABLE IF NOT EXISTS arc_meta_template (
    template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arc_meta_template_version (category_id, version_no),
    CONSTRAINT fk_arc_meta_template_category FOREIGN KEY (category_id) REFERENCES arc_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS arc_meta_field (
    field_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    field_key VARCHAR(32) NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    required TINYINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL,
    UNIQUE KEY uk_arc_meta_field_key (template_id, field_key),
    CONSTRAINT fk_arc_meta_field_template FOREIGN KEY (template_id) REFERENCES arc_meta_template (template_id),
    CONSTRAINT ck_arc_meta_field_type CHECK (field_type IN ('text', 'number', 'date'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '元数据模板',0,10,'/archive/meta-template','menuItem','C','0','1','arc:meta:view','fa fa-list-alt','admin',NOW(),'M04 元数据模板'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/archive/meta-template');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '维护元数据模板',menu_id,1,'#','','F','0','1','arc:meta:manage','#','admin',NOW(),'M04 模板维护权限'
FROM (SELECT menu_id FROM sys_menu WHERE url='/archive/meta-template' AND perms='arc:meta:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=parent.menu_id AND perms='arc:meta:manage');

COMMIT;
