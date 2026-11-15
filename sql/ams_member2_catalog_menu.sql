-- 首批 M03 菜单及权限。初始化若依和 ams_member2_catalog.sql 后执行。
START TRANSACTION;
INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案分类管理',0,7,'/archive/catalog','menuItem','C','0','1','archive:catalog:view','fa fa-sitemap','admin',NOW(),'M03 分类管理'
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE url='/archive/catalog' OR perms='archive:catalog:view');
-- 一级菜单需要显式指定内部选项卡。补齐旧安装，保留原菜单 ID 和角色授权。
UPDATE sys_menu
SET target = 'menuItem'
WHERE parent_id = 0 AND menu_type = 'C' AND url = '/archive/catalog'
  AND perms = 'archive:catalog:view' AND COALESCE(target, '') = '';
INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '新增、编辑、停用和导入',menu_id,1,'#','','F','0','1','archive:catalog:manage','#','admin',NOW(),'M03 维护权限'
FROM (SELECT menu_id FROM sys_menu WHERE perms='archive:catalog:view' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='archive:catalog:manage');
COMMIT;
