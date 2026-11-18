-- 档案工作台菜单增量；在已初始化的个人开发库执行。
-- 不建业务表，不分配固定ID，不自动给角色授权。
-- 单个部署者串行执行；执行前备份并确认目标库。
START TRANSACTION;
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案工作台',0,5,'/records/workbench','','C','0','1','archiveWorkbench','fa fa-folder-open','admin',NOW(),'records-framework-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/workbench' OR perms='archiveWorkbench');
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案登记',0,6,'/records/register','','C','0','1','archiveQuery','fa fa-edit','admin',NOW(),'records-framework-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/register' OR perms='archiveQuery');
COMMIT;
-- 管理员重新登录后查看菜单；普通角色在角色管理中授予对应页面权限。
-- 回退：先在角色管理撤销授权，再通过菜单管理删除备注为records-framework-001的两个菜单。
