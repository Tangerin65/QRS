-- 在若依初始化及 arc_category.sql 执行后导入。
START TRANSACTION;

UPDATE sys_menu
SET target = 'menuItem', perms = 'arc:category:view', is_refresh = '1'
WHERE parent_id = 0 AND menu_type = 'C' AND url = '/archive/catalog'
  AND perms IN ('archive:catalog:view', 'arc:category:view');

UPDATE sys_menu AS action
JOIN sys_menu AS parent ON action.parent_id = parent.menu_id
SET action.perms = 'arc:category:manage'
WHERE parent.url = '/archive/catalog' AND parent.perms = 'arc:category:view'
  AND action.menu_type = 'F' AND action.perms = 'archive:catalog:manage';

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案分类管理',0,7,'/archive/catalog','menuItem','C','0','1','arc:category:view','fa fa-sitemap','admin',NOW(),'M03 分类管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url = '/archive/catalog');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '新增、编辑、停用和导入',menu_id,1,'#','','F','0','1','arc:category:manage','#','admin',NOW(),'M03 维护权限'
FROM (SELECT menu_id FROM sys_menu WHERE url = '/archive/catalog' AND perms = 'arc:category:view' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = parent.menu_id AND perms = 'arc:category:manage');

COMMIT;
