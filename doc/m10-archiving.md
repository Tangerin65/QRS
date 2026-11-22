# M10 归档与库位管理

负责人：成员5（孙立家）。对应需求：FR-10（归档部分）。状态：PC 端已完成，小程序端待接入。

## 安装与初始化

1. 空库初始化只跑一个脚本：`sql/setup_qrs.sql`（含若依系统表、M01/M02/M03、M10 三张表与菜单、`arc_archive` 的归档三列）。
2. 已有库不要重跑全量脚本（含删表重建），跑脚本内的两个幂等迁移块即可：
   - `-- M10归档字段迁移`：给 `arc_archive` 补 `archive_date`、`retention_end_date`、`location_id` 与两个索引；
   - `-- M10归档与库位`：建 `arc_location`、`arc_archive_batch`、`arc_archive_batch_item` 并写入菜单。
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

- 列表只显示 `archive_status='SUBMITTED'`（待归档）的档案，字段：档号、题名、形成日期、分类、密级、保管期限、登记人、登记时间。
- 「归档校验」：勾选后逐条返回 7 个校验项（状态、档号、题名、分类启用末级、保管期限可计算、至少一个附件、无未结销毁）。
- 「提交归档」：选择库位（柜架，可空）后批量归档；每条成功/失败分别落批次明细，失败的给出原因，不会静默成功。
- 「退回补正」：待归档档案退回草稿，必填原因，写审计 `ARCHIVE_RETURN`。
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
DRAFT --提交归档(M01)--> SUBMITTED --归档校验通过(M10)--> ARCHIVED
                            |--退回补正(M10)--> DRAFT
```

归档动作在同一事务内：校验 → 更新状态与 `archive_date`/`retention_end_date`/`location_id`（带 `version` 乐观锁）→ 写批次明细 → 写业务审计 `arc_audit_event`。

## 保管期限计算

```
retention_end_date = archive_date + N 年   （有限年限）
retention_end_date = NULL                 （永久）
无法识别编码 → 校验项「保管期限可计算」不通过，阻断归档
```

编码识别按「字典键值 → 名称快照 → 直接解析」顺序，同时兼容仓库里存在的两套字典键值：

| 初始化脚本 | 键值示例 | 说明 |
| --- | --- | --- |
| `sql/setup_qrs.sql` | `PERMANENT`、`Y30` | 直接解析 |
| `sql/arc_m01_dictionary.sql` | `1`=永久、`2`=30年 … `10`=50年 | 先按 `sys_dict_data` 标签换算，字典缺失时回退 `retention_name` 快照 |

不同机器用了不同初始化脚本时，登记出来的 `retention_code` 会不同；上表两种都能正常校验与计算到期日。

到期提醒只按参数化窗口实现（当前未接定时任务），文档未规定具体提前天数。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | /records/archiving | archiveArchivingQuery | 待归档页面 |
| POST | /records/archiving/list | archiveArchivingQuery | 待归档分页（表单参数同 M01 查询） |
| POST | /records/archiving/check | archiveArchivingQuery | 批量校验，返回逐项结果 |
| POST | /records/archiving/submit | archiveArchivingSubmit | 批量归档，body：`{"archiveIds":[1,2],"locationId":3}` |
| POST | /records/archiving/return | archiveArchivingReturn | 退回补正，body：`{"archiveId":1,"version":2,"reason":"…"}` |
| GET | /records/archiving/detail/{archiveId} | archiveArchivingQuery | 档案与校验项 |
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

## 验收步骤（对应 TC-M10-01～06）

1. **正常归档**：登记草稿 → 提交 → 待归档列表勾选 → 校验全绿 → 填写库位提交归档 → 列表不再出现，数据库 `archive_status=ARCHIVED`、`archive_date`/`retention_end_date`/`location_id` 有值，批次台账出现 SUCCESS 记录。
2. **缺附件阻断**：无附件档案提交归档 → 失败原因「至少一个附件（附件 0 个）」，状态仍为 SUBMITTED。
3. **批量部分失败**：同时勾选有附件与无附件档案 → 批次状态 PARTIAL，明细逐条给出成功/失败。
4. **退回补正**：待归档档案退回并填原因 → 状态回到 DRAFT，审计出现 `ARCHIVE_RETURN`。
5. **库位保护**：删除被档案引用的柜架 → 拒绝；删除有下级的库房 → 拒绝；编码重复保存 → 拒绝。
6. **期限计算**：Y10 档案归档后 `retention_end_date` = 归档日 + 10 年；PERMANENT 档案为 NULL。

## 已知边界

- `arc_borrow`（M07）尚未建表，「无在借」校验暂不生效；建表后在校验项里补一条即可（当前 7 项，补后为 8 项）。
- `arc_destroy`（M11）尚未建表，校验项「无未结销毁申请」在表不存在时按通过处理，M11 落地后自动生效。
- 到期提醒只保留参数位，未挂 ruoyi-quartz 定时任务。
- 小程序端三个只读页面（待归档、批次、库位）未接入，接口已就绪。
