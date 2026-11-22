-- M01 档案登记菜单与按钮权限增量
-- 在 records_framework_001.sql 与 arc_m01_dictionary.sql 执行后运行
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
START TRANSACTION;

UPDATE sys_menu SET perms = 'archiveWorkbench' WHERE perms = 'records:workbench:view';
UPDATE sys_menu SET perms = 'archiveQuery' WHERE perms IN ('records:register:view', 'arc:archive:query');
UPDATE sys_menu SET perms = 'archiveAdd' WHERE perms = 'arc:archive:add';
UPDATE sys_menu SET perms = 'archiveEdit' WHERE perms = 'arc:archive:edit';
UPDATE sys_menu SET perms = 'archiveDelete' WHERE perms = 'arc:archive:del';
UPDATE sys_menu SET perms = 'archiveView' WHERE perms = 'arc:archive:view';
UPDATE sys_menu SET perms = 'archiveImport' WHERE perms = 'arc:archive:import';
UPDATE sys_menu SET perms = 'archiveExport' WHERE perms = 'arc:archive:export';
UPDATE sys_menu SET perms = 'archiveSubmit' WHERE perms = 'arc:archive:submit';
UPDATE sys_menu SET perms = 'archiveCategoryAll' WHERE perms = 'arc:archive:category:*';
UPDATE sys_menu SET perms = 'archiveSecret0' WHERE perms = 'arc:archive:secret:0';
UPDATE sys_menu SET perms = 'archiveSecret1' WHERE perms = 'arc:archive:secret:1';
UPDATE sys_menu SET perms = 'archiveSecret2' WHERE perms = 'arc:archive:secret:2';
UPDATE sys_menu SET perms = 'archiveSecret3' WHERE perms = 'arc:archive:secret:3';
UPDATE sys_menu
SET target = 'menuItem', is_refresh = '1'
WHERE parent_id = 0 AND menu_type = 'C' AND url = '/records/workbench';

UPDATE sys_menu
SET target = 'menuItem', is_refresh = '1', perms = 'archiveQuery'
WHERE parent_id = 0 AND menu_type = 'C' AND url = '/records/register'
  AND perms IN ('records:register:view', 'archiveQuery');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案登记',0,6,'/records/register','menuItem','C','0','1','archiveQuery','fa fa-edit','admin',NOW(),'M01 档案登记'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/register');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M01 操作权限'
FROM (SELECT '新增档案' menu_name,1 order_num,'archiveAdd' perms UNION ALL
      SELECT '修改档案',2,'archiveEdit' UNION ALL
      SELECT '撤销草稿',3,'archiveDelete' UNION ALL
      SELECT '查看档案详情',4,'archiveView' UNION ALL
      SELECT '导入档案',5,'archiveImport' UNION ALL
      SELECT '导出档案',6,'archiveExport' UNION ALL
      SELECT '全部档案分类',7,'archiveCategoryAll' UNION ALL
      SELECT '提交档案',12,'archiveSubmit') action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' AND perms='archiveQuery' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms);

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT CONCAT('分类范围-', c.category_name), parent.menu_id, 100 + c.category_id, '#', '', 'F', '0', '1',
       CONCAT('archiveCategory_', c.category_id), '#', 'admin', NOW(), 'M01 分类数据范围'
FROM arc_category c
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' AND perms='archiveQuery' ORDER BY menu_id LIMIT 1) parent
WHERE c.kind='category'
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=CONCAT('archiveCategory_', c.category_id));

-- 新增密级字典项后可重跑此脚本补充对应的权限节点，不自动授权角色
INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT CONCAT('密级范围-', d.dict_label), parent.menu_id, 200 + CAST(d.dict_value AS UNSIGNED), '#', '', 'F', '0', '1',
       CONCAT('archiveSecret', d.dict_value), '#', 'admin', NOW(), '允许访问数字不大于此键值的密级，字典排序不影响权限'
FROM sys_dict_data d
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' AND perms='archiveQuery' ORDER BY menu_id LIMIT 1) parent
WHERE d.dict_type='arc_secret_level' AND d.status='0'
  AND d.dict_value REGEXP '^(0|[1-9][0-9]{0,2})$' AND CAST(d.dict_value AS UNSIGNED) <= 127
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing
                   WHERE existing.parent_id=parent.menu_id AND existing.perms=CONCAT('archiveSecret', d.dict_value));

COMMIT;
-- 执行后在角色管理中为需要的角色授予档案登记及相应操作权限
