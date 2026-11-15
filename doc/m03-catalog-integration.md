# M03 分类基础接入

本次提交包含分类建表和 Java 服务。初始化若依开发库后，执行 `sql/ams_member2_catalog.sql`。已有同名表时仍可重复执行，脚本不会清空分类数据。

成员 1 在建档服务中注入 `ArchiveCatalogService`。调用 `registrationChoices(keyword, allowed)` 获取可选的末级分类，返回稳定的 `id`、分类编码、名称和完整路径。将所选 `id` 作为档案的 `categoryId`。保存档案时在业务事务中调用 `requireRegistrationCategory(categoryId, allowed)` 复核权限和状态。调用方根据当前账号计算 `allowed`，不要从客户端传入授权结果。

管理端可在检查管理权限后用 `list`、`get` 和 `save` 管理全宗、分类、档案集及逻辑文件夹。更新携带读取到的 `revision`。停用保留 ID 及历史关联，启用子节点需要已启用的上级。运行脚本后可新增全宗与下级分类，检查路径和搜索结果，再尝试循环移动、重复编码、停用有启用子项的节点和用旧版本更新。

管理页面、档案集与真实档案的关联以及其他模块分批提交。
