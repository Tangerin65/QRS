-- ============================================================
-- M12-A 模块一：账号与组织底座 菜单与权限点
-- 依赖：sql/ry_20260319.sql、sql/m12_account_001.sql 已执行
-- 特点：可重复执行（WHERE NOT EXISTS 防重）；不自动给角色授权
-- 权限点：
--   arc:account:view    账号清单、详情、统计、导出、打印
--   arc:account:manage  新增、修改、停用/启用、导入、绑定二维码
-- ============================================================
START TRANSACTION;

-- 一级菜单：档案账号管理
-- order_num 取 10：8 已被 M10「归档批次」占用，避免左侧菜单顺序不稳定
INSERT INTO sys_menu(menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
SELECT '档案账号管理', 0, 10, '/archive/account', 'menuItem', 'C', '0', '1', 'arc:account:view', 'fa fa-users', 'admin', NOW(), 'M12-A 账号与组织底座'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url = '/archive/account');

-- 按钮权限：维护权限
INSERT INTO sys_menu(menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
SELECT '新增、修改、停用、导入与绑定二维码', menu_id, 1, '#', '', 'F', '0', '1', 'arc:account:manage', '#', 'admin', NOW(), 'M12-A 维护权限'
FROM (SELECT menu_id FROM sys_menu WHERE url = '/archive/account' ORDER BY menu_id LIMIT 1) AS parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = parent.menu_id AND perms = 'arc:account:manage');

-- 按钮权限：导出（单独列出，便于只读角色单独授予导出能力）
INSERT INTO sys_menu(menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, remark)
SELECT '导出账号清单', menu_id, 2, '#', '', 'F', '0', '1', 'arc:account:export', '#', 'admin', NOW(), 'M12-A 导出权限'
FROM (SELECT menu_id FROM sys_menu WHERE url = '/archive/account' ORDER BY menu_id LIMIT 1) AS parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = parent.menu_id AND perms = 'arc:account:export');

COMMIT;

-- 使用说明：
--   管理员重新登录后可在左侧看到「档案账号管理」。
--   普通角色需在「系统管理 → 角色管理 → 修改 → 菜单权限」中勾选对应菜单与按钮后重新登录。
-- 回退说明：
--   先在角色管理撤销授权，再删除备注为 'M12-A%' 的菜单记录：
--   DELETE FROM sys_menu WHERE perms IN ('arc:account:view', 'arc:account:manage', 'arc:account:export');
