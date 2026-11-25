-- M07 借阅与审批：借阅单、审批流水，以及菜单权限。
-- 只新增业务表，不改 sys_* 表结构；执行前确认目标库（ry）。
-- 借阅状态：PENDING 待审批 → APPROVED/REJECTED；APPROVED → LENT 已借出 → RETURNED 已归还；
--           PENDING 可由申请人 WITHDRAWN 撤回。逾期不落状态，按 end_date 与当前时间计算。

CREATE TABLE IF NOT EXISTS arc_borrow (
    borrow_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '借阅单ID',
    borrow_no VARCHAR(32) NOT NULL COMMENT '借阅单号',
    archive_id BIGINT NOT NULL COMMENT '档案ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    applicant_name VARCHAR(64) NOT NULL COMMENT '申请人姓名',
    applicant_dept_id BIGINT DEFAULT NULL COMMENT '申请人部门，用于数据范围',
    reason VARCHAR(500) NOT NULL COMMENT '借阅事由',
    start_date DATE NOT NULL COMMENT '借期开始',
    end_date DATE NOT NULL COMMENT '应归还日期',
    actual_return_date DATE DEFAULT NULL COMMENT '实际归还日期',
    borrow_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '借阅状态',
    approval_level TINYINT NOT NULL DEFAULT 1 COMMENT '审批级数，本期一级',
    current_approver_id BIGINT DEFAULT NULL COMMENT '当前/最终审批人',
    lend_by BIGINT DEFAULT NULL COMMENT '借出登记人',
    lend_at DATETIME DEFAULT NULL COMMENT '借出时间',
    return_by BIGINT DEFAULT NULL COMMENT '归还登记人',
    return_at DATETIME DEFAULT NULL COMMENT '归还时间',
    renew_count INT NOT NULL DEFAULT 0 COMMENT '已续借次数',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    remark VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (borrow_id),
    UNIQUE KEY uk_arc_borrow_no (borrow_no),
    KEY idx_arc_borrow_archive (archive_id, borrow_status),
    KEY idx_arc_borrow_applicant (applicant_id, borrow_status),
    KEY idx_arc_borrow_approver (current_approver_id, borrow_status),
    KEY idx_arc_borrow_end (end_date),
    CONSTRAINT ck_arc_borrow_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借阅单';

CREATE TABLE IF NOT EXISTS arc_approval_record (
    record_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批流水ID',
    borrow_id BIGINT NOT NULL COMMENT '借阅单ID',
    approval_level TINYINT NOT NULL DEFAULT 1 COMMENT '审批级数',
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    approver_name VARCHAR(64) NOT NULL COMMENT '审批人姓名',
    result VARCHAR(16) NOT NULL COMMENT 'APPROVED/REJECTED',
    opinion VARCHAR(500) DEFAULT NULL COMMENT '审批意见，驳回必填',
    handled_at DATETIME NOT NULL COMMENT '处理时间',
    PRIMARY KEY (record_id),
    KEY idx_arc_approval_borrow (borrow_id, handled_at),
    KEY idx_arc_approval_approver (approver_id, handled_at),
    CONSTRAINT fk_arc_approval_borrow FOREIGN KEY (borrow_id) REFERENCES arc_borrow (borrow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借阅审批流水';

-- 菜单与权限点。权限名与 M01 一致使用 camelCase：borrowQuery/borrowView/borrowApply/borrowApprove/borrowLend/borrowReturn。
START TRANSACTION;
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅管理',0,7,'/borrow/list','','C','0','1','borrowQuery','fa fa-exchange','admin',NOW(),'m07-borrow-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/borrow/list' OR perms='borrowQuery');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅详情', p.menu_id,1,'','','F','0','1','borrowView','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowView');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅申请', p.menu_id,2,'','','F','0','1','borrowApply','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowApply');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅审批', p.menu_id,3,'','','F','0','1','borrowApprove','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowApprove');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '登记借出', p.menu_id,4,'','','F','0','1','borrowLend','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowLend');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '登记归还', p.menu_id,5,'','','F','0','1','borrowReturn','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowReturn');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅导出', p.menu_id,6,'','','F','0','1','borrowExport','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowExport');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '借阅导入', p.menu_id,7,'','','F','0','1','borrowImport','#','admin',NOW(),'m07-borrow-001'
FROM (SELECT menu_id FROM sys_menu WHERE perms='borrowQuery' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='borrowImport');
COMMIT;
-- 管理员重新登录后可见菜单；其他角色在角色管理中授予 borrowQuery 及对应按钮权限。
