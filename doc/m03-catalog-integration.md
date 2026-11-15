# M03 档案分类管理

## 安装

环境：JDK 17、Maven、MySQL 8。

1. 在项目开发库执行 [分类建表脚本](../sql/arc_category.sql)，再执行 [菜单脚本](../sql/arc_category_menu.sql)。空库先依次导入 `sql/ry_20260319.sql`、`sql/quartz.sql` 和 `sql/records_framework_001.sql`。两个 M03 脚本可重复执行。
2. 将 `ruoyi-admin/src/main/resources/application-local.example.yml` 复制为 `config/application-local.yml`，在本地副本填写数据库连接。在项目根目录运行 `mvn verify`，再运行 `java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,local`。
3. 登录系统，在左侧打开「档案分类管理」。普通角色在「系统管理 → 角色管理」中授权。变更权限后，重新登录查看。
   - `arc:category:view`：目录树、列表、详情、打印、导出。
   - `arc:category:manage`：在查看权限基础上，开放新增、编辑、启停、下载模板、导入。

## 页面操作

- 新增全宗后，从详情点「新增下级」。分类可挂在全宗或分类下。编辑时可调整上级、状态和顺序。
- 输入名称或编码，可同时按类型、状态筛选。统计、打印和导出均使用当前查询的全部结果。统计单位为目录节点。
- 停用目录前先停用其启用的下级。停用保留目录记录和路径，供历史档案查询。
- 导入使用页面下载的 `.xlsx` 模板，编码最多 32 位。同批新增时先填上级，再填下级。每批最多 1000 条；任一行校验失败时整批撤回。

## 建档接入

档案记录保存 `arc_category.category_id`。目录换上级时，节点 ID 保持原值，`category_path` 和所有下级路径同步更新。路径用 ID 表示，如 `/5/21/39`。状态 `0` 为启用，`1` 为停用。

建档模块注入 `ArchiveCatalogService`。调用 `registrationChoices(keyword, allowed)` 获取账号有权使用的启用末级分类，返回 ID、编码、名称和显示路径。保存前在建档事务中调用 `requireRegistrationCategory(categoryId, allowed)`。两处的 `allowed` 均由后端根据当前账号的分类范围计算。历史档案按已保存的分类 ID 查目录，允许读取停用节点。

PC 使用 `/archive/catalog` 和若依会话。IF-040～043 的 REST 路径、字段和业务码以项目接口表为准。档案集成员关联和跨部门分类范围随档案模块接入验收。

## 验收步骤

1. 分别用管理员和只读角色从首页菜单打开页面。只读角色可查询、打印和导出。直接提交写请求应被拒绝。
2. 创建两个全宗。在第一个全宗下新增两级分类，再把上级分类移到第二个全宗。检查两级目录的完整路径、子节点 ID 和顺序。
3. 用重复编码新增、把父目录移到自己的下级、停用有启用下级的目录。页面应提示原因。停用末级分类后，在建档分类查询中应找不到它，按 ID 仍能查到。
4. 分别导入包含重复项和有效层级的工作簿。检查错误行号、整批回滚、成功数量和过滤导出的上级编码。
