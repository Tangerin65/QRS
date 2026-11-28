# M10 归档与库位管理

负责人：成员5（孙立家）。对应需求：FR-10（归档部分）。状态：PC 端已完成；阶段1 整改（保管期限 UNKNOWN 阻断、校验报告可追溯）已完成；阶段2（批次/操作人、存储介质、电子存储位置、批次来源）数据模型、PC 页面与 9 个移动端接口已完成（2026-11-28）。

## 安装与初始化

1. 空库初始化只跑一个脚本：`sql/setup_qrs.sql`（含若依系统表、M01/M02/M03、M10 六张表与菜单、`arc_archive` 的阶段1/阶段2 归档列）。脚本开头的建表已包含全部列，条件 ALTER 在空库上自动跳过。
2. 已有库不要重跑全量脚本（含删表重建），按顺序跑脚本内的幂等迁移块即可：
   - `-- M10归档字段迁移`：给 `arc_archive` 补 `archive_date`、`retention_end_date`、`location_id` 与两个索引；
   - `-- M10阶段2归档属性迁移`：给 `arc_archive` 补 `storage_media`、`electronic_location`、`archived_by`、`archive_batch_id` 与 `idx_arc_archive_batch`；
   - `-- M10归档与库位`：建 `arc_location`、`arc_archive_batch`、`arc_archive_batch_item`、`arc_archive_check_report` 并写入菜单；
   - `-- M10阶段2批次来源迁移`：给 `arc_archive_batch` 补 `source_type`、`source_ref`，把存量批次回填为 `MANUAL`（阶段2 之前只有 PC 手工勾选提交一个入口），并按批次明细通用回填存量已归档档案的 `archive_batch_id` 与 `archived_by`（不写死 id，空库无副作用，重复执行不再变更）。
3. 降级：阶段2 列未迁移时后端不报错——归档退回旧语句、接口按契约返回空串，页面显示「未登记」；迁移后自动按真列返回（探测结果缓存 30 秒）。
4. 启动：`java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,local`。
5. 管理员登录后左侧出现「档案归档」「归档批次」「库位管理」三个菜单；普通角色在「系统管理 → 角色管理」授权后重新登录。

## 权限点

| 权限点 | 含义 |
| --- | --- |
| `archiveArchivingQuery` | 待归档列表、校验、统计、导出、打印 |
| `archiveArchivingSubmit` | 提交归档（批量） |
| `archiveArchivingReturn` | 退回补正 |
| `archiveBatchQuery` | 归档批次台账与明细导出 |
| `archiveLocationQuery` | 库位查询、导出、模板下载 |
| `archiveLocationManage` | 库位新增/修改/删除、Excel 导入 |

数据范围沿用 M01：`archiveCategoryAll` / `archiveCategory_{分类ID}` 控分类范围，`archiveSecret{0-3}` 控密级上限。

## 页面与操作

### 档案归档（/records/archiving）

- 列表默认只显示 `archive_status='SUBMITTED'`（待归档），状态筛选切到「已归档」可查看归档结果（同一张表，同一个数据范围）。
- 列表字段：档号、题名、形成日期、分类、密级、保管期限、状态、**批次号、介质、电子存储位置、归档人**、登记人、登记时间。最新一份校验报告为 FAIL 且未换版本的行，状态列显示黄色「待补正」；已归档显示绿色「已归档」。
- 「归档校验」：勾选后逐条返回 7 个校验项（状态、档号、题名、分类启用末级、保管期限可识别、至少一个附件、无未结销毁），并把逐项结果与当时档案版本落库为一份校验报告。
- 「提交归档」：选择库位（柜架，可空）、**存储介质（纸质/电子/混合）与电子存储位置**后批量归档。介质为电子或混合时电子存储位置必填（与小程序「加入批次」同一字段、同一错误码 `STORAGE_UNBOUND`）。每条成功/失败分别落批次明细，失败的给出原因，不会静默成功。失败时同时写一份 FAIL 报告，档案留在待归档（标「待补正」），不自动回草稿。
- 「退回补正」：待归档档案退回草稿，必填原因，写审计 `ARCHIVE_RETURN`；已归档档案不显示该操作。
- 「报告」：查看该档案最新校验报告与历史报告（来源 CHECK/SUBMIT、校验时版本、逐项结果、校验人）；档案改版后报告标记「需复检」。
- 「详情」：档案字段 + 7 项校验 + 最近校验结论 + 归档属性（批次号、介质、电子存储位置、归档人、库位路径、归档日期）；未归档的显示「未登记」。
- 支持导出 xlsx（跟随当前状态筛选）、打印当前列表、按档号/题名/分类/状态筛选与排序。
- 统计卡片：待归档、已归档、本月归档、归档批次数、部分失败批次数。

### 归档批次（/records/batch）

- 按批次号/申请人、状态、提交日期范围查询；列表显示来源（手工归档/小程序归档/重新组批，重新组批附原批次号）、总数、成功数、失败数、状态、库位编码、提交与完成时间。
- 详情弹窗显示批次来源、库位与逐条结果；明细含介质、归档人与失败原因；单批次可导出 xlsx（含批次页与明细页）。
- 支持打印台账（含来源与库位）。

### 库位管理（/records/location）

- 三级结构：库房（WAREHOUSE）→ 区域（AREA）→ 柜架（RACK），列表按层级与排序展示完整路径（如 `W01 / W01-A / W01-A-01`）。
- **介质与电子存储位置是在架档案的聚合，不是库位自身属性**：全部纸质→纸质、含电子与纸质→混合、只有电子→电子、该库位没有登记介质的档案→未登记。聚合口径与小程序库位页一致。
- 新增/修改：层级决定可选上级（库房无上级，区域挂库房，柜架挂区域）；编码全局唯一。
- 删除保护：存在下级或被档案引用（`arc_archive.location_id`）时拒绝删除。
- Excel 导入：模板列「上级编码 / 库位编码 / 库位名称 / 层级 / 容量 / 排序 / 备注」，先整表校验，任一行失败则整批不写入；同批可先填上级再填下级（最多 500 行）。
- 支持导出 xlsx、打印清单、统计卡片（各级数量、已挂档案数）。容量只展示不阻断归档。

## 状态流转

```
DRAFT --提交归档(M01)--> SUBMITTED --复检通过并归档(M10)--> ARCHIVED
                            |--复检不通过(M10)--> SUBMITTED（标「待补正」，可反复校验）
                            |--退回补正(M10)--> DRAFT
```

归档动作在同一事务内：复检 → 更新状态与 `archive_date`/`retention_end_date`/`location_id`（阶段2 再写 `storage_media`/`electronic_location`/`archived_by`/`archive_batch_id`，带 `version` 乐观锁）→ 写批次明细与校验报告 → 写业务审计 `arc_audit_event`。批次明细回答「这批哪几条失败」，`arc_archive_check_report` 回答「这一条当时为什么失败、失败在哪些项」。

## 阶段2 归档属性（2026-11-28）

| 表列 | 类型 | 上限/取值 | 写入口 |
| --- | --- | --- | --- |
| `arc_archive.storage_media` | VARCHAR(16) | `PAPER`/`ELECTRONIC`/`MIXED`，空串为未登记 | PC「提交归档」弹窗、小程序「加入批次」 |
| `arc_archive.electronic_location` | VARCHAR(500) | 电子存储位置，介质为电子/混合时必填 | 同上 |
| `arc_archive.archived_by` | VARCHAR(64) | 归档操作人，与登记人 `create_by` 区分 | 归档事务内写当前操作人 |
| `arc_archive.archive_batch_id` | BIGINT | 归档批次，索引 `idx_arc_archive_batch` | 归档事务内写成批批次 |
| `arc_archive_batch.source_type` | VARCHAR(16) | `MANUAL`/`MOBILE`/`REBATCH`/`TRANSFER`/`AUTO`，空串为未登记 | PC 记 `MANUAL`、小程序记 `MOBILE`、重新组批记 `REBATCH` |
| `arc_archive_batch.source_ref` | VARCHAR(64) | 来源引用，重新组批记原批次号 | 重新组批 |

- 落库口径：同一批次内所有档案共用批次级介质与电子存储位置（PC 与小程序都是批次级入参），不存在「同一批次一半纸质一半电子」的记账。
- 重新组批的新批次不继承原批次的介质（原批次失败项从未归档、没有介质记录），按「未登记」记账，需在下一轮归档时补。
- 存量批次由迁移脚本回填 `source_type='MANUAL'`（阶段2 之前只有 PC 手工提交）；存量已归档档案由迁移段按批次明细回填 `archive_batch_id`（最近一条 OK 记录）与 `archived_by`（该批次申请人），有据可查；`storage_media` / `electronic_location` 没有历史来源，保持空串、页面显示「未登记」，不做猜测性回填。
- 契约与页面的对应关系见 `doc/m10-mobile-api.md`；PC 与小程序同名字段：`batchNo`/`storageMedia`+`mediumLabel`/`electronicLocation`/`archivedBy`。

## 保管期限计算

```
retention_end_date = archive_date + N 年   （有限年限）
retention_end_date = NULL                 （永久）
```

识别顺序是「字典值 → 字典标签」→ 直接解析编码（字典缺项时兜底）→ `retention_name` 名称快照。字典是唯一真源，两套键值都靠标签解析，因此都能识别：

| 初始化脚本 | 键值示例 | 说明 |
| --- | --- | --- |
| `sql/setup_qrs.sql` | `PERMANENT`、`Y30` | 标签为 `永久`/`30年`，按标签解析 |
| `sql/arc_m01_dictionary.sql` | `1`=永久、`2`=30年 … `10`=50年 | 同上，键值不同不影响识别 |

不同机器用了不同初始化脚本时，登记出来的 `retention_code` 会不同；上表两种都能正常计算到期日。

保管期限在登记时即为必选项，归档校验仍把它作为第 5 项复核（`RETENTION`，标签「保管期限可识别」）。编码无法识别（UNKNOWN）时校验不通过、`submit()` 拒绝归档，不会出现 `retention_end_date` 静默留空的归档记录；批次明细记 `reason_code=RETENTION`，档案留在待归档并标「待补正」。修数据或补字典后重新校验即可，两套字典键值任选其一。

到期提醒只按参数化窗口实现（当前未接定时任务），文档未规定具体提前天数。

## 保管期限字典约定

- 字典类型固定 `arc_retention_period`，`arc_archive.retention_code` 存的是 `sys_dict_data.dict_value`；识别时只信任同一行的 `dict_label`，不硬编码任何 `dict_value`。
- `dict_value` 只是键，允许管理员改：仓库种子 `sql/arc_m01_dictionary.sql` 用 `1`..`10`，部分机器用 `PERMANENT`/`Y30`/`Y20`/`Y10`/`Y5`，两种都靠标签解析，互不影响，不要求统一键值。
- `dict_label` 约定：永久类必须含「永久」；有限年限必须是阿拉伯数字 +「年」（如 `30年`）。解析容忍 `Y30`、`30`、`30 年` 等写法。
- 识别顺序：`dict_value` → `dict_label`；键在字典里查不到时退回直接解析编码（兼容字典未初始化或被改的机器）；再退到 `retention_name` 名称快照。
- 失败分两种提示，便于定位：编码不在字典里 →「字典 arc_retention_period 中不存在该编码 X99」；标签解析不出 →「字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 9，标签 长期）」。
- 管理员把标签改成无法解析的写法后，M10 判 UNKNOWN 并阻断正式登记（校验项 `RETENTION` 不通过、提交归档被拒，档案留待补正）。这是设计保护，不是缺陷：改字典时请保持可解析写法。

## 校验报告之追溯

- 每次「归档校验」写一行 `arc_archive_check_report`（`report_source=CHECK`），每次「提交归档」的事务内复检也写一行（`SUBMIT`，带 `batch_id`）；`items_json` 存逐项 `{code,label,passed,detail}`，`report_result` 为 PASS/FAIL。
- 报告记录写入时的 `arc_archive.version`；档案被修改（版本 +1）后旧报告标记「需复检」，页面的「报告」弹窗会提示档案已改版。
- `submit()` 不复用列表页的旧报告，事务内一律重新校验并写新报告，所以归档结论永远对应当前版本，不存在拿旧报告归档。
- 失败报告就是「失败项 + 当时取值」的凭据：档案留在待归档并标「待补正」，不自动回草稿；人工「退回补正」仍按需单独操作。
- 报告表未建时（例如只升了代码没执行迁移），「报告」弹窗显示「校验报告未启用，请在数据库执行 sql/setup_qrs.sql 的 M10 段（arc_archive_check_report）后重试」，不会说成「暂无报告」。
- 降级说明：M02 版本变更事件尚不可用，本期按「每次提交前强制复检」实现；M02 能推送版本事件后，再补「版本变更即自动触发复检」，届时可省掉人工点校验。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | /records/archiving | archiveArchivingQuery | 待归档页面（含状态筛选：待归档/已归档） |
| POST | /records/archiving/list | archiveArchivingQuery | 待归档/已归档分页；行含 `batchNo`/`storageMedia`/`mediumLabel`/`electronicLocation`/`archivedBy`/`archivedAt`/`locationCode`/`locationPath` |
| POST | /records/archiving/check | archiveArchivingQuery | 批量校验，返回逐项结果，并各落库一份 `CHECK` 报告 |
| POST | /records/archiving/submit | archiveArchivingSubmit | 批量归档，body：`{"archiveIds":[1,2],"locationId":3,"storageMedia":"PAPER","electronicLocation":""}`；事务内复检并落 `SUBMIT` 报告 |
| POST | /records/archiving/return | archiveArchivingReturn | 退回补正，body：`{"archiveId":1,"version":2,"reason":"…"}` |
| GET | /records/archiving/detail/{archiveId} | archiveArchivingQuery | 档案、校验项、最近报告与是否需复检、阶段2 归档属性 |
| POST | /records/archiving/checkstates | archiveArchivingQuery | 批量取最新报告结论（列表标「待补正」用），body：`{"archiveIds":[1,2]}` |
| GET | /records/archiving/report/{archiveId} | archiveArchivingQuery | 校验报告：最新一条 + 近 20 条历史 + `stale` 是否需复检 |
| POST | /records/archiving/statistics | archiveArchivingQuery | 统计卡片 |
| GET | /records/archiving/export | archiveArchivingQuery | 导出 xlsx（跟随当前状态筛选） |
| POST | /records/batch/list | archiveBatchQuery | 批次分页；行含 `source`/`sourceLabel`/`sourceRef`/`locationCode`/`statusLabel` |
| GET | /records/batch/detail/{batchId} | archiveBatchQuery | 批次与明细（明细含 `mediumLabel`/`electronicLocation`/`archivedBy`/`locationCode`） |
| GET | /records/batch/export/{batchId} | archiveBatchQuery | 批次导出 xlsx |
| POST | /records/location/list | archiveLocationQuery | 库位列表；行含聚合口径 `medium`/`mediumLabel`/`electronicPath` |
| GET | /records/location/choices | archiveLocationQuery | 可选柜架（归档用） |
| POST | /records/location/save | archiveLocationManage | 新增/修改 |
| POST | /records/location/remove | archiveLocationManage | 删除（带保护） |
| POST | /records/location/statistics | archiveLocationQuery | 统计 |
| GET | /records/location/export | archiveLocationQuery | 导出 xlsx |
| GET | /records/location/template | archiveLocationQuery | 导入模板 |
| POST | /records/location/import | archiveLocationManage | Excel 导入 |

> 小程序端 `/api/mobile/archiving/**` 的 9 个接口已在阶段2 实现（`MobileArchivingController`），契约见 [m10-mobile-api.md](m10-mobile-api.md)。

## 验收步骤（TC-M10-01～06 + 阶段1 整改 7～9）

1. **正常归档**：登记草稿 → 提交 → 待归档列表勾选 → 校验全绿 → 填写库位提交归档 → 列表不再出现，数据库 `archive_status=ARCHIVED`、`archive_date`/`retention_end_date`/`location_id` 有值，批次台账出现 SUCCESS 记录。
2. **缺附件阻断**：无附件档案提交归档 → 失败原因「至少一个附件（附件 0 个）」，状态仍为 SUBMITTED。
3. **批量部分失败**：同时勾选有附件与无附件档案 → 批次状态 PARTIAL，明细逐条给出成功/失败。
4. **退回补正**：待归档档案退回并填原因 → 状态回到 DRAFT，审计出现 `ARCHIVE_RETURN`。
5. **库位保护**：删除被档案引用的柜架 → 拒绝；删除有下级的库房 → 拒绝；编码重复保存 → 拒绝。
6. **期限计算**：Y10 档案归档后 `retention_end_date` = 归档日 + 10 年；PERMANENT 档案为 NULL。
7. **期限无法识别阻断**：把 `retention_code` 改成字典里没有的值（如 `X99`）并清空 `retention_name` → 校验第 5 项「保管期限可识别」不通过，详情为「字典 arc_retention_period 中不存在该编码 X99」，提交归档失败且 `reason_code=RETENTION`，档案仍为 SUBMITTED、列表标「待补正」；改回 `Y30` 或 `30` 后重新校验通过，可正常归档。把字典标签改成「长期」这类写法，则提示「字典标签无法解析，应为「永久」或阿拉伯数字+年」。
8. **报告可追溯**：点「校验」→ `arc_archive_check_report` 新增一行 `report_source=CHECK`，`items_json` 含 7 项；点「报告」能看到逐项结果、校验人和校验时版本。
9. **换版本强制复检**：校验通过后修改该档案（`version+1`）→「报告」弹窗提示「档案已改版，需复检」；此时直接提交归档会在事务内重新校验并写新的 `SUBMIT` 报告，不会用旧报告放行。
10. **阶段2 归档属性**：提交归档时选「电子」但不填电子存储位置 → 弹窗提示「存储介质为电子或混合时，必须填写电子存储位置」，不产生批次；填写后归档 → `arc_archive.storage_media`/`electronic_location`/`archived_by`/`archive_batch_id` 有值，`arc_archive_batch.source_type='MANUAL'`，把状态筛选切到「已归档」能看到批次号、介质、电子存储位置、归档人四列。
11. **库位介质聚合**：把一个已归档档案的介质分别设为纸质/电子 → 库位页该柜架的「介质」列从「纸质」变为「混合」，电子存储位置列显示档案的电子路径。
12. **存量回填**：迁移段执行后，`arc_archive` 里 `archive_status='ARCHIVED'` 的存量行 `archive_batch_id`、`archived_by` 有值（分别取自批次明细最近一条 OK 记录与批次申请人），`storage_media` 仍为空、页面显示「未登记」；重跑迁移段影响 0 行。

### 阶段2 演示脚本（真数据，2026-11-28 库状态）

**前置**

- 后端为重启后的新代码（`MobileArchivingController` 已加载）；`GET /api/mobile/archiving/hub/summary` 不带令牌应返回 **401「请重新登录」**。
- 小程序绑定 **member5**（`user_id=101`、用户编号 `0005`、角色 100），MOCK 已切 `false`。
- 数据：`create_by='member5'` 的 5 条演示档案（待归档 5、6、8；已归档 4、7），批次 3 条、柜架 2 个。
- 数据范围：PC 用 admin 看全部 5 条；小程序按本人范围看的也是这 5 条，两端集合一致。

**步骤**

1. 小程序「归档」hub：待归档 **3**、需补正 **2**（档案 5、6 最近一次检验为「至少一个附件」不通过），批次数与库位指标取自全局口径。
2. 打开「待归档」，展开档案 5（`ZH-XZ-2026-0002`）与档案 6（`ZH-XZ-2026-0003`）：逐项检查里 `FILE`「至少一个附件」不通过（附件 0 个），其余项通过；点提交归档 → 按设计返回 `CHECK_NOT_PASSED`「仍有检查项未通过：至少一个附件（附件 0 个）；补正后才能归档」，档案仍为待归档、状态显示「待补正」，不自动回草稿。
3. 档案 8（`ZH-XZ-2026-000002`，附件 1、报告 PASS、期限永久）：提交归档，选介质「纸质」+ 一个启用柜架（`W01-A-01` 或 `W01-A-02`）→ 成功返回批次号，档案状态变 `ARCHIVED`。
4. 回到「批次」与「状态查询」：该档案显示已归档、介质「纸质」、批次号、归档人 `member5`；批次列表新增一条，来源为 **「小程序归档」**（`source_type='MOBILE'`）。hub 的待归档变为 **2**、已归档变为 **3**。
5. PC 端 admin 登录 → 档案归档页把状态切到「已归档」：同一条档案的介质、批次号、归档人与小程序一致；批次页「来源」列同样显示「小程序归档」。

**备注**

- 存量已归档 2 条（档案 4、7）的介质与电子存储位置为「未登记」：它们早于阶段2 归档，没有历史介质来源，不做猜测性回填，**不是缺陷**。
- 演示后可恢复归属：`.backup/` 下的备份文件可单表回滚（该目录已在 `.gitignore` 中，不入库）。
- 若要演示「介质为电子但没填电子存储位置被拦截」，在提交弹窗选「电子」直接提交即可看到 `STORAGE_UNBOUND` 文案。

## 单元测试

```
mvn -o -pl ruoyi-system -am test "-Dtest=ArchiveArchivingRetentionTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

依赖已缓存时加 `-o` 可离线跑；首次运行需联网拉 surefire 插件与 JUnit。PowerShell 里 `-Dsurefire...` 含点号，必须加引号。

- 位置：`ruoyi-system/src/test/java/com/ruoyi/system/service/ArchiveArchivingRetentionTest.java`，10 个用例。
- 覆盖：setup_qrs 键值（`PERMANENT`/`Y30`/`Y20`/`Y10`/`Y5`）与 M01 数字键值（`1`..`10`）的永久/年限识别、字典缺项退回直接解析、编码不在字典的提示、标签无法解析的提示、未知期限在 `check()` 中失败且 `submit()` 被拒、契约 §3 期限三态、阶段2 介质/来源/批次状态标签、`STORAGE_UNBOUND` 与 `PARAM_INVALID` 在提交前拦截。
- 不连库、不动字典：字典通过测试内覆写 `dictLabel` 注入；`submit()` 用假 `JdbcTemplate` + 动态代理的 `IArchiveService`，只回答归档流程用到的几条 SQL（含阶段2 列探测，假库按「未迁移」返回），遇到新查询直接失败。
- 依赖：`ruoyi-system/pom.xml` 增 `junit-jupiter`（test 作用域）与 `maven-surefire-plugin 3.5.4`（Maven 默认 3.2.5 带不动 JUnit 5.12 的 Platform 1.12）。

## 已知边界

- `arc_borrow`（M07）尚未建表（`sql/arc_borrow.sql` 是 M07 的建表脚本，未并入 `sql/setup_qrs.sql`），「无在借」校验暂不生效；建表后在校验项里补一条即可（当前 7 项，补后为 8 项）。
- `arc_destroy`（M11）尚未建表，校验项「无未结销毁申请」在表不存在时按通过处理，M11 落地后自动生效。
- 校验报告只覆盖归档校验 7 项；M02 版本事件未接入，版本变更不会自动触发复检，需人工点「校验」或直接提交触发事务内复检。
- 到期提醒只保留参数位，未挂 ruoyi-quartz 定时任务。
- 小程序待归档的 `NEED_FIX`/`UNKNOWN` 筛选与归档状态查询的期限筛选要按字典标签判定期限，SQL 无法等价表达，实现为「先按 1000 条上限扫描再内存分页」；数据量超过上限时 `total` 取上限值（`hub/summary` 响应带 `scanCapped` 标记）。
- 批次介质与电子存储位置是批次级入参，不做逐件差异化；真正「多件陆续入池、统一提交」的批次暂存池、批次审核/退回人留痕仍属后续排期（契约 §10）。
- 「正式登记后受控更正」本期未覆盖；阶段2 归档属性只写一次，暂无更正页面（需改库由管理员在库位/档案维护入口处理）。
- PC 待归档导出 xlsx 的列仍是 M01 阶段字段（不含批次号/介质/归档人），页面上可见但导出未带这三列。
