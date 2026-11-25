-- M02 档案附件权限
-- 在 arc_m01_menu.sql 执行后运行
START TRANSACTION;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M02 附件操作权限'
FROM (SELECT '查看档案附件' menu_name,20 order_num,'archiveFileView' perms UNION ALL
      SELECT '上传档案附件',21,'archiveFileUpload' UNION ALL
      SELECT '删除附件记录',22,'archiveFileDelete') action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms);

-- 工作台已作为固定首页，隐藏数据库中的重复工作台菜单与若依官网
UPDATE sys_menu SET visible='1',update_by='admin',update_time=NOW()
WHERE url='/records/workbench' OR menu_name='若依官网' OR url IN ('http://ruoyi.vip','https://ruoyi.vip','http://www.ruoyi.vip','https://www.ruoyi.vip');

COMMIT;
-- 普通角色需按需授予查看、上传和删除附件记录权限，并保留原分类与密级数据范围
