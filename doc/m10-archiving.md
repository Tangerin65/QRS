# M10 归档与库位管理

负责人：成员5（孙立家）。对应需求：FR-10（归档部分）。状态：PC 端已完成；阶段1 整改（保管期限 UNKNOWN 阻断、校验报告可追溯）已完成，小程序端待接入。

## 安装与初始化

1. 空库初始化只跑一个脚本：`sql/setup_qrs.sql`（含若依系统表、M01/M02/M03、M10 四张表与菜单、`arc_archive` 的归档三列）。
2. 已有库不要重跑全量脚本（含删表重建），跑脚本内的两个幂等迁移块即可（第二个块内含校验报告建表）：
   - `-- M10归档字段迁移`：给 `arc_archive` 补 `archive_date`、`retention_end_date`、`location_id` 与两个索引；
   - `-- M10归档与库位`：建 `arc_location`、`arc_archive_batch`、`arc_archive_batch_item` 并写入菜单；
   - `arc_archive_check_report`（M10 归档与库位块内，`CREATE TABLE IF NOT EXISTS`）：校验报告表。只补这一张表时单独执行该建表语句即可；未建表时归档主流程仍可用，报告落库/查询降级为日志 WARN，页面显示「暂无校验报告」。
3. 启动：`java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=druid,local`。
4. 管理员登录后左侧出现「档案归档」「归档批次」「库位管理」三个菜单；普通角色在「系统管理 → 角色管理」授权后重新登录。

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

- 列表只显示 `archive_status='SUBMITTED'`（待归档）的档案，字段：档号、题名、形成日期、分类、密级、保管期限、状态、登记人、登记时间。最新一份校验报告为 FAIL 的行，状态列显示黄色「待补正」。
- 「归档校验」：勾选后逐条返回 7 个校验项（状态、档号、题名、分类启用末级、保管期限可识别、至少一个附件、无未结销毁），并把逐项结果与当时档案版本落库为一份校验报告。
- 「提交归档」：选择库位（柜架，可空）后批量归档；每条成功/失败分别落批次明细，失败的给出原因，不会静默成功。失败时同时写一份 FAIL 报告，档案留在待归档（标「待补正」），不自动回草稿。
- 「退回补正」：待归档档案退回草稿，必填原因，写审计 `ARCHIVE_RETURN`。
- 「报告」：查看该档案最新校验报告与历史报告（来源 CHECK/SUBMIT、校验时版本、逐项结果、校验人）；档案改版后报告标记「需复检」。
- 支持导出 xlsx、打印当前列表、按档号/题名/分类筛选与排序。
- 统计卡片：待归档、已归档、本月归档、归档批次数、部分失败批次数。

### 归档批次（/records/batch）

- 按批次号/申请人、状态、提交日期范围查询；列表显示总数、成功数、失败数、状态、库位、提交与完成时间。
- 详情弹窗显示该批次逐条结果与失败原因；单批次可导出 xlsx（含批次页与明细页）。
- 支持打印台账。

### 库位管理（/records/location）

- 三级结构：库房（WAREHOUSE）→ 区域（AREA）→ 柜架（RACK），列表按层级与排序展示完整路径（如 `W01 / W01-A / W01-A-01`）。
- 新增/修改：层级决定可选上级（库房无上级，区域挂库房，柜架挂区域）；编码全局唯一。
- 删除保护：存在下级或被档案引用（`arc_archive.location_id`）时拒绝删除。
- Excel 导入：模板列「上级编码 / 库位编码 / 库位名称 / 层级 / 容量 / 排序 / 备注」，先整表校验，任一行失败则整批不写入；同批可先填上级再填下级（最多 500 行）。
- 支持导出 xlsx、打印清单、统计卡片（各级数量、已挂档案数）。

## 状态流转

```
DRAFT --提交归档(M01)--> SUBMITTED --复检通过并归档(M10)--> ARCHIVED
                            |--复检不通过(M10)--> SUBMITTED（标「待补正」，可反复校验）
                            |--退回补正(M10)--> DRAFT
```

归档动作在同一事务内：复检 → 更新状态与 `archive_date`/`retention_end_date`/`location_id`（带 `version` 乐观锁）→ 写批次明细与校验报告 → 写业务审计 `arc_audit_event`。批次明细回答「这批哪几条失败」，`arc_archive_check_report` 回答「这一条当时为什么失败、失败在哪些项」。

## 保管期限计算

```
retention_end_date = archive_date + N 年   （有限年限）
retention_end_date = NULL                 （永久）
```

编码识别按「字典键值 → 名称快照 → 直接解析」顺序，同时兼容仓库里存在的两套字典键值：

| 初始化脚本 | 键值示例 | 说明 |
| --- | --- | --- |
| `sql/setup_qrs.sql` | `PERMANENT`、`Y30` | 直接解析 |
| `sql/arc_m01_dictionary.sql` | `1`=永久、`2`=30年 … `10`=50年 | 先按 `sys_dict_data` 标签换算，字典缺失时回退 `retention_name` 快照 |

不同机器用了不同初始化脚本时，登记出来的 `retention_code` 会不同；上表两种都能正常计算到期日。

保管期限在登记时即为必选项，归档校验仍把它作为第 5 项复核（`RETENTION`，标签「保管期限可识别」）。编码无法识别（UNKNOWN）时校验不通过、`submit()` 拒绝归档，不会出现 `retention_end_date` 静默留空的归档记录；批次明细记 `reason_code=RETENTION`，档案留在待归档并标「待补正」。修数据或补字典后重新校验即可，两套字典键值任选其一。

到期提醒只按参数化窗口实现（当前未接定时任务），文档未规定具体提前天数。

## 校验报告之追溯

- 每次「归档校验」写一行 `arc_archive_check_report`（`report_source=CHECK`），每次「提交归档」的事务内复检也写一行（`SUBMIT`，带 `batch_id`）；`items_json` 存逐项 `{code,label,passed,detail}`，`report_result` 为 PASS/FAIL。
- 报告记录写入时的 `arc_archive.version`；档案被修改（版本 +1）后旧报告标记「需复检」，页面的「报告」弹窗会提示档案已改版。
- `submit()` 不复用列表页的旧报告，事务内一律重新校验并写新报告，所以归档结论永远对应当前版本，不存在拿旧报告归档。
- 失败报告就是「失败项 + 当时取值」的凭据：档案留在待归档并标「待补正」，不自动回草稿；人工「退回补正」仍按需单独操作。
- 降级说明：M02 版本变更事件尚不可用，本期按「每次提交前强制复检」实现；M02 能推送版本事件后，再补「版本变更即自动触发复检」，届时可省掉人工点校验。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | /records/archiving | archiveArchivingQuery | 待归档页面 |
| POST | /records/archiving/list | archiveArchivingQuery | 待归档分页（表单参数同 M01 查询） |
| POST | /records/archiving/check | archiveArchivingQuery | 批量校验，返回逐项结果，并各落库一份 `CHECK` 报告 |
| POST | /records/archiving/submit | archiveArchivingSubmit | 批量归档，body：`{"archiveIds":[1,2],"locationId":3}`；事务内复检并落 `SUBMIT` 报告 |
| POST | /records/archiving/return | archiveArchivingReturn | 退回补正，body：`{"archiveId":1,"version":2,"reason":"…"}` |
| GET | /records/archiving/detail/{archiveId} | archiveArchivingQuery | 档案、校验项、最近报告与是否需复检 |
| POST | /records/archiving/checkstates | archiveArchivingQuery | 批量取最新报告结论（列表标「待补正」用），body：`{"archiveIds":[1,2]}` |
| GET | /records/archiving/report/{archiveId} | archiveArchivingQuery | 校验报告：最新一条 + 近 20 条历史 + `stale` 是否需复检 |
| POST | /records/archiving/statistics | archiveArchivingQuery | 统计卡片 |
| GET | /records/archiving/export | archiveArchivingQuery | 待归档导出 xlsx |
| POST | /records/batch/list | archiveBatchQuery | 批次分页 |
| GET | /records/batch/detail/{batchId} | archiveBatchQuery | 批次与明细 |
| GET | /records/batch/export/{batchId} | archiveBatchQuery | 批次导出 xlsx |
| POST | /records/location/list | archiveLocationQuery | 库位列表 |
| GET | /records/location/choices | archiveLocationQuery | 可选柜架（归档用） |
| POST | /records/location/save | archiveLocationManage | 新增/修改 |
| POST | /records/location/remove | archiveLocationManage | 删除（带保护） |
| POST | /records/location/statistics | archiveLocationQuery | 统计 |
| GET | /records/location/export | archiveLocationQuery | 导出 xlsx |
| GET | /records/location/template | archiveLocationQuery | 导入模板 |
| POST | /records/location/import | archiveLocationManage | Excel 导入 |

## 验收步骤（TC-M10-01～06 + 阶段1 整改 7～9）

1. **正常归档**：登记草稿 → 提交 → 待归档列表勾选 → 校验全绿 → 填写库位提交归档 → 列表不再出现，数据库 `archive_status=ARCHIVED`、`archive_date`/`retention_end_date`/`location_id` 有值，批次台账出现 SUCCESS 记录。
2. **缺附件阻断**：无附件档案提交归档 → 失败原因「至少一个附件（附件 0 个）」，状态仍为 SUBMITTED。
3. **批量部分失败**：同时勾选有附件与无附件档案 → 批次状态 PARTIAL，明细逐条给出成功/失败。
4. **退回补正**：待归档档案退回并填原因 → 状态回到 DRAFT，审计出现 `ARCHIVE_RETURN`。
5. **库位保护**：删除被档案引用的柜架 → 拒绝；删除有下级的库房 → 拒绝；编码重复保存 → 拒绝。
6. **期限计算**：Y10 档案归档后 `retention_end_date` = 归档日 + 10 年；PERMANENT 档案为 NULL。
7. **期限无法识别阻断**：把 `retention_code` 改成字典里没有的值（如 `X99`）并清空 `retention_name` → 校验第 5 项「保管期限可识别」不通过，提交归档失败且 `reason_code=RETENTION`，档案仍为 SUBMITTED、列表标「待补正」；改回 `Y30` 或 `30` 后重新校验通过，可正常归档。
8. **报告可追溯**：点「校验」→ `arc_archive_check_report` 新增一行 `report_source=CHECK`，`items_json` 含 7 项；点「报告」能看到逐项结果、校验人和校验时版本。
9. **换版本强制复检**：校验通过后修改该档案（`version+1`）→「报告」弹窗提示「档案已改版，需复检」；此时直接提交归档会在事务内重新校验并写新的 `SUBMIT` 报告，不会用旧报告放行。

## 已知边界

- `arc_borrow`（M07）尚未建表（`sql/arc_borrow.sql` 是 M07 的建表脚本，未并入 `sql/setup_qrs.sql`），「无在借」校验暂不生效；建表后在校验项里补一条即可（当前 7 项，补后为 8 项）。
- `arc_destroy`（M11）尚未建表，校验项「无未结销毁申请」在表不存在时按通过处理，M11 落地后自动生效。
- 校验报告只覆盖归档校验 7 项；M02 版本事件未接入，版本变更不会自动触发复检，需人工点「校验」或直接提交触发事务内复检。
- 到期提醒只保留参数位，未挂 ruoyi-quartz 定时任务。
- 小程序端三个只读页面（待归档、批次、库位）未接入，接口已就绪。
