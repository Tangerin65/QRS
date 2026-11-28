-- ============================================================
-- M12-A 账号模块修复脚本：角色白名单、菜单授权、历史数据清理
-- 依赖：sql/setup_qrs.sql、sql/m12_account_001.sql、sql/m12_account_menu.sql 已执行
-- 特点：可重复执行（WHERE NOT EXISTS / 先删后插），执行前请先备份 qrs 库
-- 目标：
--   1) 普通角色 = 只读：只保留档案类菜单，收回系统管理/监控/工具（含 system:user:remove）
--   2) 新增“档案管理员”角色 = 档案类全部维护权限
--   3) 清理非 1 号账号身上的超级管理员角色
-- ============================================================
START TRANSACTION;

-- ------------------------------------------------------------
-- 1. 新建“档案管理员”角色（幂等）
--    data_scope = 1（全部数据）：档案管理员需要看到全部部门的账号
-- ------------------------------------------------------------
INSERT INTO sys_role(role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
SELECT '档案管理员', 'arc_admin', 3, '1', '0', '0', 'admin', NOW(), 'M12-A 档案账号维护角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'arc_admin' AND del_flag = '0');

-- ------------------------------------------------------------
-- 2. 普通角色数据范围统一为“全部数据”
--    原 data_scope = 2（自定义）只挂了 100/101/105 三个部门，
--    会导致 103 部门的账号在清单里“看不见”，这里一并修正。
-- ------------------------------------------------------------
UPDATE sys_role SET data_scope = '1' WHERE role_key = 'common' AND del_flag = '0';
DELETE FROM sys_role_dept WHERE role_id = (SELECT role_id FROM sys_role WHERE role_key = 'common' LIMIT 1);

-- ------------------------------------------------------------
-- 3. 收回“普通角色”的系统管理 / 系统监控 / 系统工具菜单
--    保留集合：档案工作台 2000、档案登记 2001、档案分类 2002、档案归档 2026、
--              归档批次 2027、库位管理 2028、档案账号管理 2036、
--              查看档案详情 2007、查看档案附件 2033、导出账号清单 2038
--    其余（含 system:user:add/edit/remove/import/resetPwd）全部删除
-- ------------------------------------------------------------
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.role_key = 'common'
  AND rm.menu_id NOT IN (2000, 2001, 2002, 2026, 2027, 2028, 2036, 2007, 2033, 2038);

-- ------------------------------------------------------------
-- 4. 普通角色（只读）授权：档案类页面 + 查看/导出按钮
-- ------------------------------------------------------------
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (2000, 2001, 2002, 2026, 2027, 2028, 2036, 2007, 2033, 2038)
WHERE r.role_key = 'common'
  AND r.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ------------------------------------------------------------
-- 5. 档案管理员授权：档案类全部页面 + 全部按钮
--    页面：2000/2001/2002/2026/2027/2028/2036，及这些页面下的所有按钮
--    含 2003（分类维护）、2037（账号维护）、2038（导出）、2029/2030（归档提交/退回）等
-- ------------------------------------------------------------
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON (m.menu_id IN (2000, 2001, 2002, 2026, 2027, 2028, 2036)
                    OR m.parent_id IN (2001, 2002, 2026, 2027, 2028, 2036))
WHERE r.role_key = 'arc_admin'
  AND r.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ------------------------------------------------------------
-- 6. 数据修复：非 1 号账号不得持有超级管理员角色（当前目标是 103）
-- ------------------------------------------------------------
DELETE ur FROM sys_user_role ur WHERE ur.role_id = 1 AND ur.user_id <> 1;

-- ------------------------------------------------------------
-- 7. 可选：把历史测试账号 103（登录名 147258369）改挂“档案管理员”
--    不需要这一步时，把下面 4 行删掉即可
-- ------------------------------------------------------------
INSERT INTO sys_user_role(user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_key = 'arc_admin' AND r.del_flag = '0'
WHERE u.user_id = 103
  AND u.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id);

COMMIT;

-- ------------------------------------------------------------
-- 执行结果自检（只读）
-- ------------------------------------------------------------
SELECT r.role_key, r.role_name, r.data_scope, COUNT(rm.menu_id) AS menu_count
FROM sys_role r
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id
WHERE r.del_flag = '0'
GROUP BY r.role_id, r.role_key, r.role_name, r.data_scope
ORDER BY r.role_id;

SELECT COUNT(*) AS still_super_admin_outside_user1
FROM sys_user_role
WHERE role_id = 1 AND user_id <> 1;
