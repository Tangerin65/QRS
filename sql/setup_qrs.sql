-- QRS数据库初始化（仅新空库；含删表重建）

-- 若依基础表
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  login_name        varchar(30)     not null                   comment '登录账号',
  user_name         varchar(30)     default ''                 comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户 01注册用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像路径',
  password          varchar(50)     default ''                 comment '密码',
  salt              varchar(20)     default ''                 comment '盐加密',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '29c67a30398638269fe600f73a054934', '111111', '0', '0', '127.0.0.1', null, null, 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '8e6d98b90472783cc73c17047ddccf36', '222222', '0', '0', '127.0.0.1', null, null, 'admin', sysdate(), '', null, '测试员');


drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


drop table if exists sys_role;
create table sys_role (
  role_id           bigint(20)      not null auto_increment    comment '角色ID',
  role_name         varchar(30)     not null                   comment '角色名称',
  role_key          varchar(100)    not null                   comment '角色权限字符串',
  role_sort         int(4)          not null                   comment '显示顺序',
  data_scope        char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  status            char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

insert into sys_role values('1', '超级管理员', 'admin',  1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',   'common', 2, 2, '0', '0', 'admin', sysdate(), '', null, '普通角色');


drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  url               varchar(200)    default '#'                comment '请求地址',
  target            varchar(20)     default ''                 comment '打开方式（menuItem页签 menuBlank新窗口）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  is_refresh        char(1)         default 1                  comment '是否刷新（0刷新 1不刷新）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

insert into sys_menu values('1', '系统管理', '0', '1', '#',                '',          'M', '0', '1', '', 'fa fa-gear',           'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', '#',                '',          'M', '0', '1', '', 'fa fa-video-camera',   'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', '#',                '',          'M', '0', '1', '', 'fa fa-bars',           'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', 'menuBlank', 'C', '0', '1', '', 'fa fa-location-arrow', 'admin', sysdate(), '', null, '若依官网地址');
insert into sys_menu values('100',  '用户管理', '1', '1', '/system/user',          '', 'C', '0', '1', 'system:user:view',         'fa fa-user-o',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1', '2', '/system/role',          '', 'C', '0', '1', 'system:role:view',         'fa fa-user-secret',     'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1', '3', '/system/menu',          '', 'C', '0', '1', 'system:menu:view',         'fa fa-th-list',         'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1', '4', '/system/dept',          '', 'C', '0', '1', 'system:dept:view',         'fa fa-outdent',         'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1', '5', '/system/post',          '', 'C', '0', '1', 'system:post:view',         'fa fa-address-card-o',  'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1', '6', '/system/dict',          '', 'C', '0', '1', 'system:dict:view',         'fa fa-bookmark-o',      'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1', '7', '/system/config',        '', 'C', '0', '1', 'system:config:view',       'fa fa-sun-o',           'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1', '8', '/system/notice',        '', 'C', '0', '1', 'system:notice:view',       'fa fa-bullhorn',        'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1', '9', '#',                     '', 'M', '0', '1', '',                         'fa fa-pencil-square-o', 'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2', '1', '/monitor/online',       '', 'C', '0', '1', 'monitor:online:view',      'fa fa-user-circle',     'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2', '2', '/monitor/job',          '', 'C', '0', '1', 'monitor:job:view',         'fa fa-tasks',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2', '3', '/monitor/data',         '', 'C', '0', '1', 'monitor:data:view',        'fa fa-bug',             'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2', '4', '/monitor/server',       '', 'C', '0', '1', 'monitor:server:view',      'fa fa-server',          'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2', '5', '/monitor/cache',        '', 'C', '0', '1', 'monitor:cache:view',       'fa fa-cube',            'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '表单构建', '3', '1', '/tool/build',           '', 'C', '0', '1', 'tool:build:view',          'fa fa-wpforms',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('115',  '代码生成', '3', '2', '/tool/gen',             '', 'C', '0', '1', 'tool:gen:view',            'fa fa-code',            'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('116',  '系统接口', '3', '3', '/tool/swagger',         '', 'C', '0', '1', 'tool:swagger:view',        'fa fa-gg',              'admin', sysdate(), '', null, '系统接口菜单');
insert into sys_menu values('500',  '操作日志', '108', '1', '/monitor/operlog',    '', 'C', '0', '1', 'monitor:operlog:view',     'fa fa-address-book',    'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', '/monitor/logininfor', '', 'C', '0', '1', 'monitor:logininfor:view',  'fa fa-file-image-o',    'admin', sysdate(), '', null, '登录日志菜单');
insert into sys_menu values('1000', '用户查询', '100', '1',  '#', '',  'F', '0', '1', 'system:user:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '#', '',  'F', '0', '1', 'system:user:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '#', '',  'F', '0', '1', 'system:user:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '#', '',  'F', '0', '1', 'system:user:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '#', '',  'F', '0', '1', 'system:user:export',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '#', '',  'F', '0', '1', 'system:user:import',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '#', '',  'F', '0', '1', 'system:user:resetPwd',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1007', '角色查询', '101', '1',  '#', '',  'F', '0', '1', 'system:role:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '#', '',  'F', '0', '1', 'system:role:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '#', '',  'F', '0', '1', 'system:role:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '#', '',  'F', '0', '1', 'system:role:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '#', '',  'F', '0', '1', 'system:role:export',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1012', '菜单查询', '102', '1',  '#', '',  'F', '0', '1', 'system:menu:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '#', '',  'F', '0', '1', 'system:menu:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '#', '',  'F', '0', '1', 'system:menu:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '#', '',  'F', '0', '1', 'system:menu:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1016', '部门查询', '103', '1',  '#', '',  'F', '0', '1', 'system:dept:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '#', '',  'F', '0', '1', 'system:dept:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '#', '',  'F', '0', '1', 'system:dept:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '#', '',  'F', '0', '1', 'system:dept:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1020', '岗位查询', '104', '1',  '#', '',  'F', '0', '1', 'system:post:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '#', '',  'F', '0', '1', 'system:post:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '#', '',  'F', '0', '1', 'system:post:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '#', '',  'F', '0', '1', 'system:post:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '#', '',  'F', '0', '1', 'system:post:export',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1025', '字典查询', '105', '1',  '#', '',  'F', '0', '1', 'system:dict:list',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2',  '#', '',  'F', '0', '1', 'system:dict:add',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3',  '#', '',  'F', '0', '1', 'system:dict:edit',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4',  '#', '',  'F', '0', '1', 'system:dict:remove',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5',  '#', '',  'F', '0', '1', 'system:dict:export',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1030', '参数查询', '106', '1',  '#', '',  'F', '0', '1', 'system:config:list',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2',  '#', '',  'F', '0', '1', 'system:config:add',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3',  '#', '',  'F', '0', '1', 'system:config:edit',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4',  '#', '',  'F', '0', '1', 'system:config:remove',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5',  '#', '',  'F', '0', '1', 'system:config:export',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1035', '公告查询', '107', '1',  '#', '',  'F', '0', '1', 'system:notice:list',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2',  '#', '',  'F', '0', '1', 'system:notice:add',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3',  '#', '',  'F', '0', '1', 'system:notice:edit',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4',  '#', '',  'F', '0', '1', 'system:notice:remove',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1039', '操作查询', '500', '1',  '#', '',  'F', '0', '1', 'monitor:operlog:list',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2',  '#', '',  'F', '0', '1', 'monitor:operlog:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '详细信息', '500', '3',  '#', '',  'F', '0', '1', 'monitor:operlog:detail',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1042', '日志导出', '500', '4',  '#', '',  'F', '0', '1', 'monitor:operlog:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录查询', '501', '1',  '#', '',  'F', '0', '1', 'monitor:logininfor:list',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '登录删除', '501', '2',  '#', '',  'F', '0', '1', 'monitor:logininfor:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '日志导出', '501', '3',  '#', '',  'F', '0', '1', 'monitor:logininfor:export',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1046', '账户解锁', '501', '4',  '#', '',  'F', '0', '1', 'monitor:logininfor:unlock',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '在线查询', '109', '1',  '#', '',  'F', '0', '1', 'monitor:online:list',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '批量强退', '109', '2',  '#', '',  'F', '0', '1', 'monitor:online:batchForceLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1049', '单条强退', '109', '3',  '#', '',  'F', '0', '1', 'monitor:online:forceLogout',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务查询', '110', '1',  '#', '',  'F', '0', '1', 'monitor:job:list',                '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务新增', '110', '2',  '#', '',  'F', '0', '1', 'monitor:job:add',                 '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务修改', '110', '3',  '#', '',  'F', '0', '1', 'monitor:job:edit',                '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '任务删除', '110', '4',  '#', '',  'F', '0', '1', 'monitor:job:remove',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '状态修改', '110', '5',  '#', '',  'F', '0', '1', 'monitor:job:changeStatus',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1055', '任务详细', '110', '6',  '#', '',  'F', '0', '1', 'monitor:job:detail',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '任务导出', '110', '7',  '#', '',  'F', '0', '1', 'monitor:job:export',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成查询', '115', '1',  '#', '',  'F', '0', '1', 'tool:gen:list',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '生成修改', '115', '2',  '#', '',  'F', '0', '1', 'tool:gen:edit',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '生成删除', '115', '3',  '#', '',  'F', '0', '1', 'tool:gen:remove',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '预览代码', '115', '4',  '#', '',  'F', '0', '1', 'tool:gen:preview',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1061', '生成代码', '115', '5',  '#', '',  'F', '0', '1', 'tool:gen:code',     '#', 'admin', sysdate(), '', null, '');


drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');
insert into sys_role_menu values ('2', '1061');

drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');

drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1,  '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
insert into sys_config values(2,  '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456');
insert into sys_config values(3,  '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深黑主题theme-dark，浅色主题theme-light，深蓝主题theme-blue');
insert into sys_config values(4,  '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(5,  '用户管理-密码字符范围',         'sys.account.chrtype',              '0',             'Y', 'admin', sysdate(), '', null, '默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');
insert into sys_config values(6,  '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(7,  '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8,  '主框架页-菜单导航显示风格',     'sys.index.menuStyle',              'default',       'Y', 'admin', sysdate(), '', null, '菜单导航显示风格（default为左侧导航菜单，topnav为顶部导航菜单）');
insert into sys_config values(9,  '主框架页-是否开启页脚',         'sys.index.footer',                 'true',          'Y', 'admin', sysdate(), '', null, '是否开启底部页脚显示（true显示，false隐藏）');
insert into sys_config values(10, '主框架页-是否开启页签',         'sys.index.tagsView',               'true',          'Y', 'admin', sysdate(), '', null, '是否开启菜单多页签显示（true显示，false隐藏）');
insert into sys_config values(11, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');


drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  login_name     varchar(50)    default ''                comment '登录账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


drop table if exists sys_user_online;
create table sys_user_online (
  sessionId         varchar(50)   default ''                comment '用户会话id',
  login_name        varchar(50)   default ''                comment '登录账号',
  dept_name         varchar(50)   default ''                comment '部门名称',
  ipaddr            varchar(128)  default ''                comment '登录IP地址',
  login_location    varchar(255)  default ''                comment '登录地点',
  browser           varchar(50)   default ''                comment '浏览器类型',
  os                varchar(50)   default ''                comment '操作系统',
  status            varchar(10)   default ''                comment '在线状态on_line在线off_line离线',
  start_timestamp   datetime                                comment 'session创建时间',
  last_access_time  datetime                                comment 'session最后访问时间',
  expire_time       int(5)        default 0                 comment '超时时间，单位为分钟',
  session_data      blob          default null              comment '序列化的Session数据，用于服务重启后恢复会话',
  primary key (sessionId)
) engine=innodb comment = '在线用户记录';


drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  start_time          datetime                                  comment '执行开始时间',
  end_time            datetime                                  comment '执行结束时间',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('3', '若依开源框架介绍', '1', '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1705030583977401651/5ed5db6a_1151004.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>', '0', 'admin', sysdate(), '', null, '管理员');


drop table if exists sys_notice_read;
create table sys_notice_read (
  read_id          bigint(20)       not null auto_increment    comment '已读主键',
  notice_id        int(4)           not null                   comment '公告id',
  user_id          bigint(20)       not null                   comment '用户id',
  read_time        datetime         not null                   comment '阅读时间',
  primary key (read_id),
  unique key uk_user_notice (user_id, notice_id)   comment '同一用户同一公告只记录一次'
) engine=innodb auto_increment=1 comment='公告已读记录表';


drop table if exists gen_table;
create table gen_table (
  table_id             bigint(20)      not null auto_increment    comment '编号',
  table_name           varchar(200)    default ''                 comment '表名称',
  table_comment        varchar(500)    default ''                 comment '表描述',
  sub_table_name       varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name    varchar(64)     default null               comment '子表关联的外键名',
  class_name           varchar(100)    default ''                 comment '实体类名称',
  tpl_category         varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作 sub主子表操作）',
  package_name         varchar(100)                               comment '生成包路径',
  module_name          varchar(30)                                comment '生成模块名',
  business_name        varchar(30)                                comment '生成业务名',
  function_name        varchar(50)                                comment '生成功能名',
  function_author      varchar(50)                                comment '生成功能作者',
  form_col_num         int(1)          default 1                  comment '表单布局（单列 双列 三列）',
  gen_type             char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path             varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options              varchar(1000)                              comment '其它生成选项',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time 	       datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';

-- Quartz定时任务表
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;

-- 档案工作台菜单
START TRANSACTION;
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案工作台',0,5,'/records/workbench','','C','0','1','archiveWorkbench','fa fa-folder-open','admin',NOW(),'records-framework-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/workbench' OR perms='archiveWorkbench');
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案登记',0,6,'/records/register','','C','0','1','archiveQuery','fa fa-edit','admin',NOW(),'records-framework-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/register' OR perms='archiveQuery');
COMMIT;


-- 档案分类目录
CREATE TABLE IF NOT EXISTS arc_category_edit_lock (
    id TINYINT PRIMARY KEY
) ENGINE=InnoDB;
INSERT IGNORE INTO arc_category_edit_lock(id) VALUES (1);

CREATE TABLE IF NOT EXISTS arc_category (
    category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(32) NOT NULL,
    category_name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    parent_id BIGINT NULL,
    category_path VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 启用，1 停用',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NOT NULL DEFAULT '',
    revision INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arc_category_code (category_code),
    KEY idx_arc_category_parent (parent_id),
    KEY idx_arc_category_path (category_path),
    CONSTRAINT fk_arc_category_parent FOREIGN KEY (parent_id) REFERENCES arc_category(category_id),
    CONSTRAINT ck_arc_category_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 分类管理权限
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


-- M01档案登记表

CREATE TABLE IF NOT EXISTS arc_archive_sequence (
    category_id BIGINT NOT NULL,
    forming_year SMALLINT NOT NULL,
    next_value INT NOT NULL DEFAULT 1,
    PRIMARY KEY (category_id, forming_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案编号序列';

CREATE TABLE IF NOT EXISTS arc_archive (
    archive_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_code VARCHAR(96) NOT NULL,
    title VARCHAR(200) NOT NULL,
    forming_date DATE NOT NULL,
    category_id BIGINT NOT NULL,
    category_code VARCHAR(32) NOT NULL,
    secret_level TINYINT NOT NULL DEFAULT 0,
    retention_code VARCHAR(32) NOT NULL DEFAULT '',
    retention_name VARCHAR(100) NOT NULL DEFAULT '',
    summary VARCHAR(1000) NOT NULL DEFAULT '',
    metadata_json JSON NOT NULL,
    archive_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    archive_date DATE DEFAULT NULL,
    retention_end_date DATE DEFAULT NULL,
    location_id BIGINT DEFAULT NULL,
    storage_media VARCHAR(16) NOT NULL DEFAULT '' COMMENT '存储介质：PAPER/ELECTRONIC/MIXED，空为未登记',
    electronic_location VARCHAR(500) NOT NULL DEFAULT '' COMMENT '电子存储位置（介质为电子/混合时填写）',
    archived_by VARCHAR(64) NOT NULL DEFAULT '' COMMENT '归档操作人，与登记人 create_by 区分',
    archive_batch_id BIGINT DEFAULT NULL COMMENT '归档批次 arc_archive_batch.batch_id',
    version INT NOT NULL DEFAULT 1,
    dept_id BIGINT NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (archive_id),
    UNIQUE KEY uk_arc_archive_code (archive_code),
    KEY idx_arc_archive_category (category_id, del_flag, archive_status),
    KEY idx_arc_archive_owner (create_by, del_flag, archive_status),
    KEY idx_arc_archive_dept (dept_id, del_flag, archive_status),
    KEY idx_arc_archive_forming_date (forming_date),
    KEY idx_arc_archive_retention_end (retention_end_date),
    KEY idx_arc_archive_location (location_id),
    KEY idx_arc_archive_batch (archive_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案登记信息';

CREATE TABLE IF NOT EXISTS arc_audit_event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_id BIGINT DEFAULT NULL,
    archive_code VARCHAR(96) DEFAULT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detail VARCHAR(500) NOT NULL DEFAULT '',
    PRIMARY KEY (event_id),
    KEY idx_arc_audit_archive_time (archive_id, event_time),
    KEY idx_arc_audit_actor_time (actor_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案业务审计事件';


-- M01保管期限与密级字典
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
START TRANSACTION;

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
SELECT '档案保管期限','arc_retention_period','0','admin',NOW(),'档案登记的保管期限选项'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='arc_retention_period');

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
SELECT '档案密级','arc_secret_level','0','admin',NOW(),'键值须为0至127的规范整数，数字越大密级越高，排序仅控制显示'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='arc_secret_level');

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
SELECT item.dict_sort,item.dict_label,item.dict_value,'arc_retention_period','','','N','0','admin',NOW(),'M01 初始保管期限，可由管理员维护'
FROM (SELECT 1 dict_sort,'永久' dict_label,'PERMANENT' dict_value UNION ALL
      SELECT 2,'30年','Y30' UNION ALL
      SELECT 3,'20年','Y20' UNION ALL
      SELECT 4,'10年','Y10' UNION ALL
      SELECT 5,'5年','Y5') item
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data existing
                  WHERE existing.dict_type='arc_retention_period' AND existing.dict_value=item.dict_value);

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
SELECT item.dict_sort,item.dict_label,item.dict_value,'arc_secret_level','','','N','0','admin',NOW(),'数字越大密级越高，角色权限为 archiveSecret 加此键值'
FROM (SELECT 1 dict_sort,'公开' dict_label,'0' dict_value UNION ALL
      SELECT 2,'内部','1' UNION ALL
      SELECT 3,'秘密','2' UNION ALL
      SELECT 4,'机密','3') item
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data existing
                  WHERE existing.dict_type='arc_secret_level' AND existing.dict_value=item.dict_value);

COMMIT;


-- M01保管期限快照迁移

SET @has_retention_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'retention_name'
);
SET @retention_sql = IF(@has_retention_name = 0,
    'ALTER TABLE arc_archive ADD COLUMN retention_name VARCHAR(100) NOT NULL DEFAULT '''' AFTER retention_code',
    'SELECT 1');
PREPARE retention_stmt FROM @retention_sql;
EXECUTE retention_stmt;
DEALLOCATE PREPARE retention_stmt;

UPDATE arc_archive a
JOIN sys_dict_data d ON d.dict_type COLLATE utf8mb4_0900_ai_ci = _utf8mb4'arc_retention_period' COLLATE utf8mb4_0900_ai_ci
    AND d.dict_value COLLATE utf8mb4_0900_ai_ci = a.retention_code COLLATE utf8mb4_0900_ai_ci
SET a.retention_name = d.dict_label
WHERE a.retention_name = '';


-- M10归档字段迁移（归档日期、保管到期日、库位）

SET @has_archive_date = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'archive_date'
);
SET @archive_date_sql = IF(@has_archive_date = 0,
    'ALTER TABLE arc_archive ADD COLUMN archive_date DATE DEFAULT NULL AFTER archive_status',
    'SELECT 1');
PREPARE archive_date_stmt FROM @archive_date_sql;
EXECUTE archive_date_stmt;
DEALLOCATE PREPARE archive_date_stmt;

SET @has_retention_end_date = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'retention_end_date'
);
SET @retention_end_date_sql = IF(@has_retention_end_date = 0,
    'ALTER TABLE arc_archive ADD COLUMN retention_end_date DATE DEFAULT NULL AFTER archive_date',
    'SELECT 1');
PREPARE retention_end_date_stmt FROM @retention_end_date_sql;
EXECUTE retention_end_date_stmt;
DEALLOCATE PREPARE retention_end_date_stmt;

SET @has_location_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'location_id'
);
SET @location_id_sql = IF(@has_location_id = 0,
    'ALTER TABLE arc_archive ADD COLUMN location_id BIGINT DEFAULT NULL AFTER retention_end_date',
    'SELECT 1');
PREPARE location_id_stmt FROM @location_id_sql;
EXECUTE location_id_stmt;
DEALLOCATE PREPARE location_id_stmt;

SET @has_idx_retention_end = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND index_name = 'idx_arc_archive_retention_end'
);
SET @idx_retention_end_sql = IF(@has_idx_retention_end = 0,
    'ALTER TABLE arc_archive ADD KEY idx_arc_archive_retention_end (retention_end_date)',
    'SELECT 1');
PREPARE idx_retention_end_stmt FROM @idx_retention_end_sql;
EXECUTE idx_retention_end_stmt;
DEALLOCATE PREPARE idx_retention_end_stmt;

SET @has_idx_location = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND index_name = 'idx_arc_archive_location'
);
SET @idx_location_sql = IF(@has_idx_location = 0,
    'ALTER TABLE arc_archive ADD KEY idx_arc_archive_location (location_id)',
    'SELECT 1');
PREPARE idx_location_stmt FROM @idx_location_sql;
EXECUTE idx_location_stmt;
DEALLOCATE PREPARE idx_location_stmt;


-- M10阶段2归档属性迁移（存储介质、电子存储位置、归档人、归档批次）

SET @has_storage_media = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'storage_media'
);
SET @storage_media_sql = IF(@has_storage_media = 0,
    'ALTER TABLE arc_archive ADD COLUMN storage_media VARCHAR(16) NOT NULL DEFAULT '''' COMMENT ''存储介质：PAPER/ELECTRONIC/MIXED，空为未登记'' AFTER location_id',
    'SELECT 1');
PREPARE storage_media_stmt FROM @storage_media_sql;
EXECUTE storage_media_stmt;
DEALLOCATE PREPARE storage_media_stmt;

SET @has_electronic_location = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'electronic_location'
);
SET @electronic_location_sql = IF(@has_electronic_location = 0,
    'ALTER TABLE arc_archive ADD COLUMN electronic_location VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''电子存储位置（介质为电子/混合时填写）'' AFTER storage_media',
    'SELECT 1');
PREPARE electronic_location_stmt FROM @electronic_location_sql;
EXECUTE electronic_location_stmt;
DEALLOCATE PREPARE electronic_location_stmt;

SET @has_archived_by = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'archived_by'
);
SET @archived_by_sql = IF(@has_archived_by = 0,
    'ALTER TABLE arc_archive ADD COLUMN archived_by VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''归档操作人，与登记人 create_by 区分'' AFTER electronic_location',
    'SELECT 1');
PREPARE archived_by_stmt FROM @archived_by_sql;
EXECUTE archived_by_stmt;
DEALLOCATE PREPARE archived_by_stmt;

SET @has_archive_batch_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'archive_batch_id'
);
SET @archive_batch_id_sql = IF(@has_archive_batch_id = 0,
    'ALTER TABLE arc_archive ADD COLUMN archive_batch_id BIGINT DEFAULT NULL COMMENT ''归档批次 arc_archive_batch.batch_id'' AFTER archived_by',
    'SELECT 1');
PREPARE archive_batch_id_stmt FROM @archive_batch_id_sql;
EXECUTE archive_batch_id_stmt;
DEALLOCATE PREPARE archive_batch_id_stmt;

SET @has_idx_archive_batch = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND index_name = 'idx_arc_archive_batch'
);
SET @idx_archive_batch_sql = IF(@has_idx_archive_batch = 0,
    'ALTER TABLE arc_archive ADD KEY idx_arc_archive_batch (archive_batch_id)',
    'SELECT 1');
PREPARE idx_archive_batch_stmt FROM @idx_archive_batch_sql;
EXECUTE idx_archive_batch_stmt;
DEALLOCATE PREPARE idx_archive_batch_stmt;


-- M01档案登记权限
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


-- M02附件表
CREATE TABLE IF NOT EXISTS arc_file (
    file_id BIGINT NOT NULL AUTO_INCREMENT,
    archive_id BIGINT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    store_key VARCHAR(128) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked TINYINT NOT NULL DEFAULT 0 COMMENT '附件撤销标记：0有效，1已撤销',
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_arc_file_store_key (store_key),
    KEY idx_arc_file_archive (archive_id, file_id, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案附件';

-- 兼容在已有 arc_file 的数据库上重复执行安装脚本。
SET @m02_add_revoked = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema=DATABASE() AND table_name='arc_file' AND column_name='revoked'),
    'SELECT 1',
    'ALTER TABLE arc_file ADD COLUMN revoked TINYINT NOT NULL DEFAULT 0 COMMENT ''附件撤销标记：0有效，1已撤销''');
PREPARE m02_stmt FROM @m02_add_revoked;
EXECUTE m02_stmt;
DEALLOCATE PREPARE m02_stmt;

-- M02 文件版本表
CREATE TABLE IF NOT EXISTS arc_file_version (
    file_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    store_key VARCHAR(128) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_version_no INT DEFAULT NULL,
    PRIMARY KEY (file_id, version_no),
    KEY idx_arc_file_version_created (file_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案附件文件版本';

INSERT INTO arc_file_version
    (file_id, version_no, original_name, store_key, file_type, file_size, sha256, create_by, create_time)
SELECT f.file_id, 1, f.display_name, f.store_key, f.file_type, f.file_size, f.sha256, f.create_by, f.create_time
FROM arc_file f
LEFT JOIN arc_file_version v ON v.file_id=f.file_id AND v.version_no=1
WHERE v.file_id IS NULL;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M02 文件利用权限'
FROM (
    SELECT '预览档案文件' menu_name,23 order_num,'archiveFilePreview' perms UNION ALL
    SELECT '下载档案文件',24,'archiveFileDownload' UNION ALL
    SELECT '打印档案文件',25,'archiveFilePrint'
) action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms
);

-- M10归档与库位

CREATE TABLE IF NOT EXISTS arc_location (
    location_id   BIGINT NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT DEFAULT NULL,
    location_level VARCHAR(16) NOT NULL DEFAULT 'RACK',
    location_code VARCHAR(32) NOT NULL,
    location_name VARCHAR(120) NOT NULL,
    capacity      INT DEFAULT NULL,
    status        TINYINT NOT NULL DEFAULT 0,
    sort_no       INT NOT NULL DEFAULT 0,
    remark        VARCHAR(500) NOT NULL DEFAULT '',
    create_by     VARCHAR(64) NOT NULL DEFAULT '',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64) DEFAULT NULL,
    update_time   DATETIME DEFAULT NULL,
    PRIMARY KEY (location_id),
    UNIQUE KEY uk_arc_location_code (location_code),
    KEY idx_arc_location_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='档案库位';

CREATE TABLE IF NOT EXISTS arc_archive_batch (
    batch_id       BIGINT NOT NULL AUTO_INCREMENT,
    batch_no       VARCHAR(32) NOT NULL,
    applicant_id   BIGINT NOT NULL,
    applicant_name VARCHAR(64) NOT NULL DEFAULT '',
    total_count    INT NOT NULL DEFAULT 0,
    success_count  INT NOT NULL DEFAULT 0,
    fail_count     INT NOT NULL DEFAULT 0,
    batch_status   VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    location_id    BIGINT DEFAULT NULL,
    source_type    VARCHAR(16) NOT NULL DEFAULT '' COMMENT '批次来源：TRANSFER/AUTO/MANUAL/REBATCH/MOBILE，空为未登记',
    source_ref     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源引用，REBATCH 记原批次号',
    submit_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time    DATETIME DEFAULT NULL,
    remark         VARCHAR(500) NOT NULL DEFAULT '',
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_arc_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档批次';

CREATE TABLE IF NOT EXISTS arc_archive_batch_item (
    item_id      BIGINT NOT NULL AUTO_INCREMENT,
    batch_id     BIGINT NOT NULL,
    archive_id   BIGINT NOT NULL,
    archive_code VARCHAR(96) NOT NULL DEFAULT '',
    title        VARCHAR(200) NOT NULL DEFAULT '',
    item_result  VARCHAR(8) NOT NULL,
    reason_code  VARCHAR(32) NOT NULL DEFAULT '',
    reason       VARCHAR(200) NOT NULL DEFAULT '',
    PRIMARY KEY (item_id),
    KEY idx_arc_batch_item_batch (batch_id, item_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档批次明细';

-- 归档校验报告：每次校验/提交复检的逐项结果，带当时档案版本，换版本即需复检
CREATE TABLE IF NOT EXISTS arc_archive_check_report (
    report_id       BIGINT NOT NULL AUTO_INCREMENT,
    archive_id      BIGINT NOT NULL,
    archive_code    VARCHAR(96) NOT NULL DEFAULT '',
    title           VARCHAR(200) NOT NULL DEFAULT '',
    archive_version INT NOT NULL DEFAULT 0 COMMENT '校验时的 arc_archive.version',
    batch_id        BIGINT DEFAULT NULL COMMENT 'SUBMIT 报告所属批次，CHECK 为 NULL',
    report_source   VARCHAR(16) NOT NULL DEFAULT 'CHECK' COMMENT 'CHECK 人工校验 / SUBMIT 归档复检',
    report_result   VARCHAR(8) NOT NULL COMMENT 'PASS / FAIL',
    item_total      INT NOT NULL DEFAULT 0,
    fail_count      INT NOT NULL DEFAULT 0,
    items_json      TEXT NOT NULL COMMENT '逐项结果：[{code,label,passed,detail}]',
    checked_by_id   BIGINT NOT NULL DEFAULT 0,
    checked_by      VARCHAR(64) NOT NULL DEFAULT '',
    check_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (report_id),
    KEY idx_arc_check_archive (archive_id, report_id),
    KEY idx_arc_check_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档校验报告';


-- M10阶段2批次来源迁移（arc_archive_batch.source_type / source_ref）

SET @has_batch_table = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive_batch'
);
SET @has_source_type = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive_batch' AND column_name = 'source_type'
);
SET @source_type_sql = IF(@has_batch_table = 1 AND @has_source_type = 0,
    'ALTER TABLE arc_archive_batch ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT '''' COMMENT ''批次来源：TRANSFER/AUTO/MANUAL/REBATCH/MOBILE，空为未登记'' AFTER location_id',
    'SELECT 1');
PREPARE source_type_stmt FROM @source_type_sql;
EXECUTE source_type_stmt;
DEALLOCATE PREPARE source_type_stmt;

SET @has_source_ref = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive_batch' AND column_name = 'source_ref'
);
SET @source_ref_sql = IF(@has_batch_table = 1 AND @has_source_ref = 0,
    'ALTER TABLE arc_archive_batch ADD COLUMN source_ref VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''来源引用，REBATCH 记原批次号'' AFTER source_type',
    'SELECT 1');
PREPARE source_ref_stmt FROM @source_ref_sql;
EXECUTE source_ref_stmt;
DEALLOCATE PREPARE source_ref_stmt;

-- 阶段2 之前只有 PC 手工勾选提交一种入口，存量批次统一按 MANUAL 记账（移动端来源 MOBILE、重新组批 REBATCH 由代码写入）
SET @backfill_source_sql = IF(@has_batch_table = 1,
    'UPDATE arc_archive_batch SET source_type = ''MANUAL'' WHERE source_type = ''''',
    'SELECT 1');
PREPARE backfill_source_stmt FROM @backfill_source_sql;
EXECUTE backfill_source_stmt;
DEALLOCATE PREPARE backfill_source_stmt;

-- 存量已归档档案回填阶段2 属性：通用条件，不写死 id；空库无副作用，重复执行不再变更
SET @has_archive_batch_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'archive_batch_id'
);
SET @has_archived_by = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arc_archive' AND column_name = 'archived_by'
);

-- 批次号：取该档案在批次明细里最近一条 OK 记录所属批次
SET @backfill_batch_id_sql = IF(@has_batch_table = 1 AND @has_archive_batch_id = 1,
    'UPDATE arc_archive a JOIN arc_archive_batch_item i ON i.item_id = (SELECT i2.item_id FROM arc_archive_batch_item i2 WHERE i2.archive_id = a.archive_id AND i2.item_result = ''OK'' ORDER BY i2.item_id DESC LIMIT 1) SET a.archive_batch_id = i.batch_id WHERE a.archive_status = ''ARCHIVED'' AND a.del_flag = ''0'' AND a.archive_batch_id IS NULL',
    'SELECT 1');
PREPARE backfill_batch_id_stmt FROM @backfill_batch_id_sql;
EXECUTE backfill_batch_id_stmt;
DEALLOCATE PREPARE backfill_batch_id_stmt;

-- 归档人：批次号已有但 archived_by 为空时，取该批次申请人（与小程序/PC 的「归档人」同义）
SET @backfill_archived_by_sql = IF(@has_batch_table = 1 AND @has_archived_by = 1,
    'UPDATE arc_archive a JOIN arc_archive_batch b ON b.batch_id = a.archive_batch_id SET a.archived_by = b.applicant_name WHERE a.archive_status = ''ARCHIVED'' AND a.del_flag = ''0'' AND CHAR_LENGTH(a.archived_by) = 0',
    'SELECT 1');
PREPARE backfill_archived_by_stmt FROM @backfill_archived_by_sql;
EXECUTE backfill_archived_by_stmt;
DEALLOCATE PREPARE backfill_archived_by_stmt;


-- M10归档与库位权限
START TRANSACTION;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '档案归档',0,7,'/records/archiving','menuItem','C','0','1','archiveArchivingQuery','fa fa-archive','admin',NOW(),'M10 档案归档'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/archiving');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '归档批次',0,8,'/records/batch','menuItem','C','0','1','archiveBatchQuery','fa fa-list-alt','admin',NOW(),'M10 归档批次'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/batch');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '库位管理',0,9,'/records/location','menuItem','C','0','1','archiveLocationQuery','fa fa-cubes','admin',NOW(),'M10 库位管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/records/location');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M10 归档操作权限'
FROM (SELECT '提交归档' menu_name,1 order_num,'archiveArchivingSubmit' perms UNION ALL
      SELECT '归档退回补正',2,'archiveArchivingReturn') action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/archiving' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms);

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M10 库位操作权限'
FROM (SELECT '维护库位' menu_name,1 order_num,'archiveLocationManage' perms) action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/location' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms);

COMMIT;

-- M02附件权限
START TRANSACTION;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT action.menu_name,parent.menu_id,action.order_num,'#','','F','0','1',action.perms,'#','admin',NOW(),'M02 附件操作权限'
FROM (SELECT '查看档案附件' menu_name,20 order_num,'archiveFileView' perms UNION ALL
      SELECT '上传档案附件',21,'archiveFileUpload' UNION ALL
      SELECT '删除附件记录',22,'archiveFileDelete') action
JOIN (SELECT menu_id FROM sys_menu WHERE url='/records/register' ORDER BY menu_id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.parent_id=parent.menu_id AND existing.perms=action.perms);

UPDATE sys_menu SET visible='1',update_by='admin',update_time=NOW()
WHERE url='/records/workbench' OR menu_name='若依官网' OR url IN ('http://ruoyi.vip','https://ruoyi.vip','http://www.ruoyi.vip','https://www.ruoyi.vip');

COMMIT;

-- M04 元数据模板
-- 同一分类的版本各占一行，字段通过 template_id 关联到具体版本。
CREATE TABLE IF NOT EXISTS arc_meta_template (
    template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_arc_meta_template_version (category_id, version_no),
    CONSTRAINT fk_arc_meta_template_category FOREIGN KEY (category_id) REFERENCES arc_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS arc_meta_field (
    field_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    field_key VARCHAR(32) NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    required TINYINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL,
    UNIQUE KEY uk_arc_meta_field_key (template_id, field_key),
    CONSTRAINT fk_arc_meta_field_template FOREIGN KEY (template_id) REFERENCES arc_meta_template (template_id),
    CONSTRAINT ck_arc_meta_field_type CHECK (field_type IN ('text', 'number', 'date'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ============================================================
-- M12-A 账号与组织底座（合并自 sql/m12_account_001.sql）
-- 依赖：本文件上方的若依基础表（sys_user / sys_dept / sys_role / sys_user_role）
--
-- 【维护须知】新增 sys_user 字段必须用下面的 ALTER TABLE + information_schema 判断，
-- 不要改写上方第 36 行起的 create table sys_user。因为该建表语句之后紧跟的两条
-- insert 是「按位置赋值」且没有写字段名，一旦在 create table 中间插入新列，
-- 这两条 insert 会立刻报 ERROR 1136 Column count doesn't match value count。
--
-- 本段可重复执行：已存在时走 DO 0，不做任何修改。
-- ============================================================
START TRANSACTION;

-- 1. sys_user.user_no 业务用户编号（对外业务编号，二维码内容）
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

-- 2. sys_user 档案业务字段
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

-- 3. wx_binding 微信身份绑定表
--    模块一只建表并在详情里展示绑定状态，不写绑定业务；绑定/解绑在模块二
--    一个 open_id 同时只允许绑定一个平台账号 → open_id 唯一
--    bind_version 在解绑/停用时递增，用于让该账号的旧令牌全部失效
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


-- ============================================================
-- M12-B 微信绑定与小程序自动登录（合并自 sql/m12_binding_001.sql）
-- 依赖：M12-A 已执行（wx_binding 在模块一已建，这里用 IF NOT EXISTS 再兜一次）
-- 本段可重复执行。
-- ============================================================
START TRANSACTION;

-- wx_binding 兜底（模块一已建）
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

-- wx_token 小程序会话令牌
--    token_digest 是令牌明文的 SHA-256 十六进制串（64 位），明文不落库；
--    bind_version 记录签发时的绑定版本，与 wx_binding.bind_version 比对即可让旧令牌失效；
--    revoked_at 非空表示已撤销（主动退出或管理员强制下线）。
CREATE TABLE IF NOT EXISTS wx_token (
    token_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '令牌ID',
    token_digest CHAR(64)     NOT NULL COMMENT '令牌明文的 SHA-256 摘要（十六进制）',
    user_id      BIGINT       NOT NULL COMMENT '平台账号ID（sys_user.user_id）',
    bind_version INT          NOT NULL COMMENT '签发时的绑定版本',
    expires_at   DATETIME     NOT NULL COMMENT '到期时间',
    revoked_at   DATETIME     NULL COMMENT '撤销时间，非空即已失效',
    client_type  VARCHAR(20)  NOT NULL DEFAULT 'miniapp' COMMENT '客户端类型（miniapp 小程序 / pc 电脑端）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_wx_token_digest (token_digest),
    KEY idx_wx_token_user (user_id, revoked_at),
    KEY idx_wx_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序会话令牌';

COMMIT;


-- ============================================================
-- M12-A 账号菜单与权限点（合并自 sql/m12_account_menu.sql）
-- 依赖：M12-A 表结构已执行
-- 特点：可重复执行（WHERE NOT EXISTS 防重）；不自动给角色授权
-- 权限点：
--   arc:account:view    账号清单、详情、统计、导出、打印
--   arc:account:manage  新增、修改、停用/启用、导入、绑定二维码
--   arc:account:export  单独授予「导出」能力
-- 说明：order_num 取 10，8 已被 M10「归档批次」占用，避免左侧菜单顺序不稳定。
--       普通角色需在「系统管理 → 角色管理 → 修改 → 菜单权限」中勾选后重新登录。
-- ============================================================
START TRANSACTION;

-- 一级菜单：档案账号管理
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

-- M12 回退说明：
--   1) 先在角色管理撤销授权，再删除备注为 'M12-A%' 的菜单记录：
--      DELETE FROM sys_menu WHERE perms IN ('arc:account:view', 'arc:account:manage', 'arc:account:export');
--   2) DROP TABLE wx_token;
--      DROP TABLE wx_binding;
--      ALTER TABLE sys_user DROP INDEX uk_sys_user_user_no;
--      ALTER TABLE sys_user DROP INDEX idx_sys_user_dept_status;
--      ALTER TABLE sys_user DROP COLUMN user_no, DROP COLUMN arc_archivist, DROP COLUMN arc_secret_level;

START TRANSACTION;

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '元数据模板',0,10,'/archive/meta-template','menuItem','C','0','1','arc:meta:view','fa fa-list-alt','admin',NOW(),'M04 元数据模板'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/archive/meta-template');

INSERT INTO sys_menu(menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '维护元数据模板',menu_id,1,'#','','F','0','1','arc:meta:manage','#','admin',NOW(),'M04 模板维护权限'
FROM (SELECT menu_id FROM sys_menu WHERE url='/archive/meta-template' AND perms='arc:meta:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=parent.menu_id AND perms='arc:meta:manage');

COMMIT;
