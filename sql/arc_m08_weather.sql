-- ============================================================
-- M08 气象数据采集与管理 建表与菜单脚本
-- 对应详细设计：表 3-10 weather_city、表 3-11 weather_forecast、表 3-12 weather_collect_log
-- 字段编号：DR-113 cityCode、DR-115 collectStatus、DR-116 weatherText、DR-117/118 温度、
--            DR-119 humidity、DR-120 windLevel、DR-121 dataSource、DR-122 forecastTime、
--            DR-123 collectTime、DR-124 lastSuccessAt、DR-125 statusMessage
-- 可重复执行；不重建 sys_* 表。
-- ============================================================

CREATE TABLE IF NOT EXISTS weather_city (
    city_code    VARCHAR(20)  NOT NULL COMMENT '行政区划城市编码 DR-113',
    city_name    VARCHAR(64)  NOT NULL COMMENT '城市显示名',
    latitude     DECIMAL(9,6) DEFAULT NULL COMMENT '纬度（采集器调用天气源使用）',
    longitude    DECIMAL(9,6) DEFAULT NULL COMMENT '经度（采集器调用天气源使用）',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否参与采集',
    data_source  VARCHAR(128) DEFAULT NULL COMMENT '首选数据源标识',
    remark       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
    create_by    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (city_code),
    KEY idx_weather_city_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='M08 采集城市配置';

CREATE TABLE IF NOT EXISTS weather_forecast (
    forecast_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '预报记录主键',
    city_code     VARCHAR(20)  NOT NULL COMMENT '城市编码 DR-113',
    forecast_time DATETIME     NOT NULL COMMENT '预报有效时间 DR-122（业务键之一）',
    forecast_date DATE         DEFAULT NULL COMMENT '旧接口兼容字段 DR-114',
    weather_text  VARCHAR(64)  NOT NULL COMMENT '天气现象 DR-116',
    temp_min      DECIMAL(5,2) NOT NULL COMMENT '最低温 DR-117',
    temp_max      DECIMAL(5,2) NOT NULL COMMENT '最高温 DR-118',
    humidity      DECIMAL(5,2) DEFAULT NULL COMMENT '相对湿度 DR-119',
    wind_level    VARCHAR(32)  DEFAULT NULL COMMENT '风力描述 DR-120',
    data_source   VARCHAR(128) NOT NULL COMMENT '数据来源标识 DR-121',
    collect_time  DATETIME     NOT NULL COMMENT '服务端接收时间 DR-123',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    revoked       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已撤销（逻辑删除）',
    revoked_at    DATETIME     DEFAULT NULL COMMENT '撤销时间',
    revoked_by    VARCHAR(64)  DEFAULT NULL COMMENT '撤销人',
    revoke_reason VARCHAR(255) DEFAULT NULL COMMENT '撤销原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (forecast_id),
    UNIQUE KEY uk_weather_city_time (city_code, forecast_time),
    KEY idx_weather_forecast_time (forecast_time),
    KEY idx_weather_forecast_status (revoked, forecast_time),
    CONSTRAINT ck_weather_temp CHECK (temp_max >= temp_min),
    CONSTRAINT ck_weather_humidity CHECK (humidity IS NULL OR (humidity >= 0 AND humidity <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='M08 天气预报记录';

CREATE TABLE IF NOT EXISTS weather_collect_log (
    collect_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '采集运行主键',
    city_code       VARCHAR(20)  NOT NULL COMMENT '目标城市',
    collect_status  VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAILED/STALE DR-115',
    forecast_count  INT          NOT NULL DEFAULT 0 COMMENT '本次接收预报条数',
    accepted_count  INT          NOT NULL DEFAULT 0 COMMENT '本次入库条数',
    last_success_at DATETIME     DEFAULT NULL COMMENT '最后成功采集时间 DR-124',
    error_code      VARCHAR(32)  DEFAULT NULL COMMENT '错误码',
    message         VARCHAR(255) DEFAULT NULL COMMENT '脱敏摘要 DR-125',
    request_id      VARCHAR(64)  DEFAULT NULL COMMENT '采集请求编号',
    collect_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    PRIMARY KEY (collect_id),
    KEY idx_weather_log_city (city_code, collect_time),
    KEY idx_weather_log_status (collect_status, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='M08 采集运行记录';

-- 默认城市（成都/北京/上海），坐标供 Open-Meteo 采集使用
INSERT INTO weather_city (city_code, city_name, latitude, longitude, enabled, data_source, create_by)
SELECT * FROM (
    SELECT '510100' AS city_code, '成都市' AS city_name, 30.66342 AS latitude, 104.07208 AS longitude, 1 AS enabled, 'open-meteo' AS data_source, 'admin' AS create_by
    UNION ALL SELECT '110000', '北京市', 39.90420, 116.40740, 1, 'open-meteo', 'admin'
    UNION ALL SELECT '310000', '上海市', 31.23040, 121.47370, 1, 'open-meteo', 'admin'
) t WHERE NOT EXISTS (SELECT 1 FROM weather_city w WHERE w.city_code = t.city_code);

START TRANSACTION;

-- 菜单：气象管理（C）与权限点
INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '气象管理',0,9,'/weather/list','','C','0','1','weatherQuery','fa fa-cloud','admin',NOW(),'m08-weather-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE url='/weather/list');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '气象查询',(SELECT menu_id FROM sys_menu WHERE url='/weather/list' LIMIT 1),1,'#','','F','0','1','weatherQuery','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='weatherQuery' AND menu_type='F');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '气象维护',(SELECT menu_id FROM sys_menu WHERE url='/weather/list' LIMIT 1),2,'#','','F','0','1','weatherManage','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='weatherManage' AND menu_type='F');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '气象导出',(SELECT menu_id FROM sys_menu WHERE url='/weather/list' LIMIT 1),3,'#','','F','0','1','weatherExport','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='weatherExport' AND menu_type='F');

INSERT INTO sys_menu (menu_name,parent_id,order_num,url,target,menu_type,visible,is_refresh,perms,icon,create_by,create_time,remark)
SELECT '气象采集',(SELECT menu_id FROM sys_menu WHERE url='/weather/list' LIMIT 1),4,'#','','F','0','1','weatherCollect','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='weatherCollect' AND menu_type='F');

-- 管理员角色与普通角色默认可见（普通角色后续按需授予）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE (m.url='/weather/list' OR m.perms IN ('weatherQuery','weatherManage','weatherExport','weatherCollect'))
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=m.menu_id);

COMMIT;
