-- Apply after setup_qrs.sql to add the M05 menu without modifying archive records.
INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案检索',0,8,'/records/search','menuItem','C','0','1','archiveSearch','fa fa-search','admin',NOW(),'M05 档案检索'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/search');
