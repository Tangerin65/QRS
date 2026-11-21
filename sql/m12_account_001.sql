-- ============================================================
-- M12-A 模块一：账号与组织底座 增量脚本
-- 依赖：sql/ry_20260319.sql 已初始化（sys_user / sys_dept / sys_role / sys_user_role）
-- 特点：可重复执行；不新建平行账号体系，只复用若依 sys_user / sys_dept / sys_role
-- 执行者：单个部署者串行执行；执行前备份并确认目标库
--
-- 本脚本新增：
--   1) sys_user.user_no          业务「用户编号」，绑定二维码只放这个值，可保留前导零
--   2) sys_user.arc_archivist    是否档案员
--   3) sys_user.arc_secret_level 密级上限引用（规则判定归 M13 成员1，本模块只存引用）
--   4) sys_user 清单索引
--   5) wx_binding                微信身份绑定表（本模块只建表与展示状态，绑定业务在模块二）
-- ============================================================
START TRANSACTION;

-- ------------------------------------------------------------
-- 1. sys_user.user_no 业务用户编号
--    MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，统一用 information_schema 判断后动态执行，
--    保证脚本可重复执行（第二次执行时走 DO 0，不做任何修改）。
-- ------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user ADD COLUMN user_no VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''用户编号（对外业务编号，二维码内容）'' AFTER user_id',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'user_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量账号补编号：按 user_id 左补零到 4 位，例如 1 → 0001。已是 4 位以上时保持不变。
UPDATE sys_user SET user_no = LPAD(user_id, 4, '0') WHERE user_no = '' OR user_no IS NULL;

-- 用户编号跨账号唯一（也正是二维码绑定的查找依据）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_user_no (user_no)',
        'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_sys_user_user_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. sys_user 档案业务字段
-- ------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user ADD COLUMN arc_archivist CHAR(1) NOT NULL DEFAULT ''0'' COMMENT ''是否档案员（0否 1是）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'arc_archivist'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user ADD COLUMN arc_secret_level VARCHAR(20) NOT NULL DEFAULT '''' COMMENT ''密级上限引用（代码值，规则由 M13 判定）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'arc_secret_level'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 清单按「部门 + 状态」组合筛选，补一个联合索引
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_user ADD INDEX idx_sys_user_dept_status (dept_id, status)',
        'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'idx_sys_user_dept_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. wx_binding 微信身份绑定表
--    本模块（W1）只建表并在详情里展示绑定状态，不写绑定业务；绑定/解绑在模块二（W2）
--    一个 open_id 同时只允许绑定一个平台账号 → open_id 唯一
--    bind_version 在解绑/停用时递增，用于让该账号的旧令牌全部失效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_binding (
    binding_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
    user_id      BIGINT       NOT NULL COMMENT '平台账号ID（sys_user.user_id）',
    open_id      VARCHAR(64)  NOT NULL COMMENT '微信 OpenID（仅服务端用 code 换取）',
    union_id     VARCHAR(64)  NULL COMMENT '微信 UnionID（同一开放平台下才有）',
    bind_version INT          NOT NULL DEFAULT 1 COMMENT '绑定版本，解绑/停用时递增',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 有效，0 已解绑',
    bound_at     DATETIME     NULL COMMENT '绑定时间',
    unbound_at   DATETIME     NULL COMMENT '解绑时间',
    create_by    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_wx_binding_open_id (open_id),
    KEY idx_wx_binding_user (user_id, status),
    CONSTRAINT ck_wx_binding_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信身份绑定';

COMMIT;

-- 回退说明：
--   1) DROP TABLE wx_binding;
--   2) ALTER TABLE sys_user DROP INDEX uk_sys_user_user_no;
--      ALTER TABLE sys_user DROP INDEX idx_sys_user_dept_status;
--      ALTER TABLE sys_user DROP COLUMN user_no, DROP COLUMN arc_archivist, DROP COLUMN arc_secret_level;
