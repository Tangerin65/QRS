-- M01 保管期限与密级字典初始化
-- 只补充缺少的字典类型与键值，不覆盖管理员维护的数据
-- 在若依基础表已存在时执行，之后可运行 arc_m01_menu.sql 补充密级权限节点
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
START TRANSACTION;

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
SELECT '档案保管期限','arc_retention_period','0','admin',NOW(),'档案登记的保管期限选项'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='arc_retention_period');

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
SELECT '档案密级','arc_secret_level','0','admin',NOW(),'键值须为0至127的规范整数，数字越大密级越高，排序仅控制显示'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='arc_secret_level');

-- 十个初始期限选项，可在字典管理中修改名称或增加其他期限
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
SELECT item.dict_sort,item.dict_label,item.dict_value,'arc_retention_period','','','N','0','admin',NOW(),'M01 初始保管期限，可由管理员维护'
FROM (SELECT 1 dict_sort,'永久' dict_label,'1' dict_value UNION ALL
      SELECT 2,'30年','2' UNION ALL
      SELECT 3,'20年','3' UNION ALL
      SELECT 4,'10年','4' UNION ALL
      SELECT 5,'5年','5' UNION ALL
      SELECT 6,'1年','6' UNION ALL
      SELECT 7,'3年','7' UNION ALL
      SELECT 8,'15年','8' UNION ALL
      SELECT 9,'25年','9' UNION ALL
      SELECT 10,'50年','10') item
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data existing
                  WHERE existing.dict_type='arc_retention_period' AND existing.dict_value=item.dict_value);

-- 密级键值决定权限上限，已使用的键值不要改作其他含义
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
SELECT item.dict_sort,item.dict_label,item.dict_value,'arc_secret_level','','','N','0','admin',NOW(),'数字越大密级越高，角色权限为 archiveSecret 加此键值'
FROM (SELECT 1 dict_sort,'公开' dict_label,'0' dict_value UNION ALL
      SELECT 2,'内部','1' UNION ALL
      SELECT 3,'秘密','2' UNION ALL
      SELECT 4,'机密','3') item
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data existing
                  WHERE existing.dict_type='arc_secret_level' AND existing.dict_value=item.dict_value);

COMMIT;
-- 执行后在字典管理刷新缓存并重新打开登记页
-- 既有档案的期限名称快照和角色权限均不会被此脚本修改
