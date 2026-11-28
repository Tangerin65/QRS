# M10 归档 · 小程序接口契约（阶段2 已实现）

- 评审对象：`E:\WorkSpace\Common\档案管理系统\M10-小程序-页面与接口桩.md`（小程序 Bot，阶段1 交付）
- 评审人：「归档」（M10 后端负责人）。结论：**草案整体可用，接口数 8 → 9，检查项 code、错误码、批次退回口径需修正**，明细见 §9。
- 状态：**已实现**（2026-11-28，`ruoyi-admin/.../controller/archive/MobileArchivingController.java`）。本文与 `doc/m10-archiving.md` 冲突时以本文为准（只限 `/api/mobile/**`）。
- 实现后仍未变的口径：检查项 code 由后端出、业务失败走 HTTP 400 + 字符串码、批次状态只用 `RUNNING/SUCCESS/PARTIAL/FAILED`、`returnedBy/returnedAt` 本期为空串。

## 1. 通用约定

| 项 | 约定 |
| --- | --- |
| 前缀 | `/api/mobile/archiving/**`，与现有 `/api/mobile/archives`、`/api/mobile/borrow` 一致 |
| 鉴权 | 控制器 `@Anonymous` + 手工校验 `Authorization: Bearer <token>`（`wx_token` + `wx_binding`，与 `MobileArchiveSearchController` 同一套）；失败 401「请重新登录」；`wx_token`/`wx_binding` 两张表都未建时 503「小程序账号服务尚未接入」（见下） |
| 成功 | HTTP 200 + `{"code":0,"msg":"操作成功","data":{…}}`。若依 `AjaxResult.Type.SUCCESS` 的值是 **0**（真接口证据：小程序 `qrs-mini-shots/evidence/01-hub-summary.json` 返回 `code:0`）；小程序 `utils/request.js` 对 `0` 与 `200` 都判成功 |
| 业务失败 | HTTP 400 + `{"code":"<业务码>","msg":"<中文文案>","data":{…}}` |
| 权限不足 | HTTP 403，`msg` 用通用文案「当前账号没有执行此操作的权限」 |
| 服务未接入 | HTTP 503「小程序账号服务尚未接入」：`wx_token`/`wx_binding` 未建表（缺 M12 段初始化）时返回。语义是「依赖服务未部署」，与 500「代码异常」区分，排障时不会被误判成归档接口 bug；与 `MobileArchiveSearchController` 的同一情形保持同码 |
| 未预期异常 | HTTP 500 +「归档操作暂时失败」，服务端记日志 |
| 分页 | `pageNum` 默认 1；`pageSize` 默认 20、上限 50；返回 `rows/total/pageNum/pageSize/hasMore` |
| ID / 时间 | 所有 id 用字符串（Long → String，避免 JS 精度问题）；时间为 `yyyy-MM-dd HH:mm:ss`、日期 `yyyy-MM-dd`，空值给 `""` 而不是 null |
| 缓存 | 响应头 `Cache-Control: no-store` |
| 数据范围 | 沿用 M01：非管理员只看本人登记的档案；分类授权（`archiveCategoryAll` / `archiveCategory_{id}`）与密级上限（`archiveSecret{0-3}`）同样生效 |

> 为什么业务失败必须走 HTTP 400：小程序 `utils/request.js` 只在非 2xx 时回退读 `msg`；2xx + 字符串码会落到「操作未完成，请稍后重试」的兜底文案，页面就看不到具体原因。

## 2. 权限（复用 PC 权限码，不新增）

| 接口 | 所需权限 |
| --- | --- |
| hub/summary、pending/list、check、status/search | `archiveArchivingQuery` |
| pending/join-batch、batch/retry | `archiveArchivingSubmit` |
| batch/list、batch/detail | `archiveBatchQuery` |
| location/list | `archiveLocationQuery` |

答复草案遗留 2：不需要 `archivingView` 这类新权限码，移动端按 `menus.selectPermsByUserId` 判定同一批权限即可。

## 3. 检查项 code 口径（后端唯一，前端不得自编）

| code | label（后端中文） | 通过条件 | 失败 detail 示例 |
| --- | --- | --- | --- |
| `STATUS` | 档案处于待归档状态 | `archive_status='SUBMITTED'` | 当前状态 ARCHIVED |
| `CODE` | 档号已生成 | `archive_code` 非空 | 档号为空 |
| `TITLE` | 题名完整 | `title` 非空 | 题名为空 |
| `CATEGORY` | 分类为启用末级 | 分类在启用末级可选集合内 | 分类已停用或不是末级 |
| `RETENTION` | 保管期限可识别 | 字典标签可解析为永久或年限 | 字典 arc_retention_period 中不存在该编码 X99 |
| `FILE` | 至少一个附件 | 附件数 > 0 | 附件 0 个 |
| `DESTROY` | 无未结销毁申请 | 未结销毁单 = 0 | 未结销毁单 1 条 |

- 前端只按 `code` 做图标/分组，展示一律用后端返回的 `label` + `detail`，不要自己翻译。
- 草案里的 `METADATA`/`READABLE`/`UNIQUE_CODE`/`PAPER_DIGITAL`/`SECURITY` **本期不存在**：分别属于 M04 元数据模板、M05 检索、后续一致性校验，未接入不虚构。

保管期限统一三态（所有返回 retention 的接口同构）：

```json
{ "code": "Y30", "label": "30 年", "state": "FIXED", "years": 30, "message": "" }
```

`state`：`PERMANENT` | `FIXED` | `UNKNOWN`；`years` 仅 FIXED 有值；`message` 仅 UNKNOWN 有值（两种原因：字典中不存在该编码 / 字典标签无法解析）。

## 4. 接口明细（9 个）

### 4.1 `GET /api/mobile/archiving/hub/summary`

| 字段 | 类型 | 口径 |
| --- | --- | --- |
| pending | int | 待归档（`SUBMITTED`，范围内） |
| needFix | int | 待归档中「保管期限 UNKNOWN」或「最近报告 FAIL」 |
| unknownRetention | int | 待归档中保管期限 UNKNOWN |
| batchOpen | int | 批次 `batch_status='RUNNING'` |
| batchReturned | int | 批次 `batch_status in ('PARTIAL','FAILED')` |
| locationAvailable | int | 启用（status=0）且未满（capacity 为空或 used<capacity）的柜架 |
| locationTotal | int | 全部柜架数 |
| archivedThisMonth | int | 本月归档数（可选，PC 统计口径一致） |

实现提示：`needFix`/`unknownRetention` 需要逐条判定期限，别每次请求都查字典——一次 `select dict_value,dict_label from sys_dict_data where dict_type='arc_retention_period' and status='0'` 读全量后在内存判定。

### 4.2 `POST /api/mobile/archiving/pending/list`

入参：`pageNum`、`pageSize`、`filter`（`ALL`|`NEED_FIX`|`UNKNOWN`）、`keyword`（档号/题名，可选）、`categoryId`（可选）。

`rows[]`：

| 字段 | 说明 |
| --- | --- |
| archiveId, archiveCode, title, categoryName, formingDate | 档案基础信息 |
| retention | 三态对象（§3） |
| medium / mediumLabel | `PAPER`/`ELECTRONIC`/`MIXED`，未登记为 `""`（页面显示「未登记」） |
| storageText | 库位路径 + 电子存储位置拼接（两者都有时用 ` · ` 连接） |
| batchNo | 所属批次（`arc_archive.archive_batch_id` → `batch_no`），未归档 `""` |
| version | 乐观锁版本，仅用于展示与「档案已改版需复检」提示；归档接口不要求传 |
| check | `{passed,total,tone,label,checkedAt}`，`tone` 取 `pass`/`warn`/`fail` |
| checks | 最近一份报告的逐项结果（`code/label/passed/detail`），未校验给 `[]` |

- 列表**不写报告**（避免翻页刷出大量 CHECK 报告），要新结果调 §4.3。
- `NEED_FIX`/`UNKNOWN` 的筛选在 SQL 层做（期限用 `sys_dict_data` 子查询、`NEED_FIX` 用「最新报告 FAIL」子查询），不要全量取出后内存分页，否则 `total` 不准。

### 4.3 `POST /api/mobile/archiving/check`（替换草案 `status/recheck`，两个页面共用）

入参：`archiveCode` 或 `archiveId`（二选一，`archiveId` 优先）。
出参：`{archiveId, archiveCode, pass, retention, reportId, reportEnabled, checkedAt, checks[]}`。
行为：真实执行 M10 的 `check()` 并落一份 `CHECK` 报告（`report_source='CHECK'`），与 PC「校验」完全同一条路径。

- `reportEnabled=false` 表示 `arc_archive_check_report` 未建表：`pass`/`checks` 照常返回，`reportId=""`。
- 允许对任意可见档案检查；档案不是待归档时 `STATUS` 项会给出失败 detail，接口本身不报错。

### 4.4 `POST /api/mobile/archiving/pending/join-batch`

入参：`archiveCode`（必填）、`locationId`（可选）、`storageMedia`（`PAPER`/`ELECTRONIC`/`MIXED`，可空）、`electronicLocation`（电子存储位置，介质为电子/混合时必填）。
**不传 version**：服务端在事务内按当时读到的版本做乐观锁，移动端持有旧版本只会让用户白重试一次。
出参：`{batchNo, archiveId, archiveCode, archiveStatus:"ARCHIVED", locationCode, archivedAt}`。
行为：校验 → 单件归档（等价 PC 单条提交，创建新批次）。**幂等**：档案已 `ARCHIVED` 时直接返回其批次号，不报错，前端提示「已归档」。
错误：`ARCHIVE_NOT_FOUND`、`ARCHIVE_STATUS_CONFLICT`、`VERSION_CONFLICT`、`RETENTION_UNRECOGNIZED`、`CHECK_NOT_PASSED`（`data.items` 带失败项）、`LOCATION_NOT_FOUND`、`LOCATION_DISABLED`、`STORAGE_UNBOUND`。

> 口径说明：本期后端没有「批次暂存池」，所以「加入批次」= 单件组批并立即归档。真正的多件陆续入池、统一提交留阶段3（见 §10 待拍板 1）。

> 失败提交的批次行为（PC 与小程序一致，不是移动端独有）：`submit()` 是 PC 与小程序共用的服务方法，**校验未通过的提交同样会落一条 `batch_status='FAILED'`（部分失败为 `PARTIAL`）的批次与对应明细**，档案留在待归档（标「待补正」），并写一份 `SUBMIT` 校验报告。这条批次不是脏数据：它用于审计「谁在什么时候提交过、为什么没归档」，也是 §4.8「重新组批」的数据来源（取该批次的 `FAIL` 明细）。因此提交失败后批次列表会多一条失败批次，这是预期行为。

### 4.5 `POST /api/mobile/archiving/status/search`

入参：`pageNum`、`pageSize`、`keyword`（档号/题名，≤200 字）、`filter`（`ALL`|`PERMANENT`|`FIXED`|`UNKNOWN`）、`archiveStatus`（可选：`ALL`/`DRAFT`/`SUBMITTED`/`ARCHIVED`）。

`rows[]`：`archiveId, archiveCode, title, retention, medium, mediumLabel, archiveStatus`（`DRAFT`/`SUBMITTED`/`ARCHIVED`）、`statusLabel`、`batchNo`、`locationCode`、`locationPath`、`archivedAt`、`lastCheckAt`、`version`、`check`、`checks[]`。

错误：`PARAM_INVALID`（关键词超长/枚举越界）。

### 4.6 `POST /api/mobile/archiving/batch/list`

入参：`pageNum`、`pageSize`、`filter`（`ALL`|`OPEN`|`RETURNED`）、`keyword`（批次号/申请人）、`from`、`to`（提交日期 `yyyy-MM-dd`）。

`rows[]`：

| 字段 | 说明 |
| --- | --- |
| batchId, batchNo | |
| name | 后端派生：`remark` 非空用 remark，否则「{applicantName} {提交日期} 归档批次」（不新增列） |
| source / sourceLabel / sourceDetail | `TRANSFER`/`AUTO`/`MANUAL`/`REBATCH`/`MOBILE`；空串 label「未登记」，`REBATCH` 的 detail 为「原批次 {sourceRef}」 |
| total / success / fail | 批次计数 |
| retentionText | 批次内档案期限聚合，如「永久 3 / 30 年 7」（取字典 label 原文）；实现成本高可先给 `""` |
| status / statusLabel | `RUNNING`/`SUCCESS`/`PARTIAL`/`FAILED` → 进行中/已完成/部分失败/全部失败 |
| applicantName、locationCode | 申请人、批次库位 |
| createdAt / updatedAt | `submit_time` / `finish_time` |
| canRetry | `fail_count > 0` |
| returnedReason | 失败明细聚合（`reason` 去重后拼接，截断 200 字）；无失败为 `""` |
| returnedBy / returnedAt | **本期无批次审核流，固定 `""`** |

筛选映射：`OPEN` = `RUNNING`；`RETURNED` = `PARTIAL`/`FAILED`（有失败项需补正重报）。

### 4.7 `GET /api/mobile/archiving/batch/detail?batchNo=`

出参：`batch`（字段同 §4.6 单行）+ `items[]`：`archiveId, archiveCode, title, itemResult`（`OK`/`FAIL`）、`reasonCode`（`CHECK`/`RETENTION`/`CONFLICT`/`DENIED`）、`reason`、`medium`/`mediumLabel`、`archivedBy`、`checkReportId`（可选）。

> `medium`/`mediumLabel`/`archivedBy` 与 §4.2/§4.5 同名同义，取自 `arc_archive.storage_media` / `archived_by`；PC 批次明细的「介质」「归档人」两列走同一个服务方法 `batchItemFacts()`，两端同口径。未登记时为 `""`/`"未登记"`/`""`。
错误：`BATCH_NOT_FOUND`。

### 4.8 `POST /api/mobile/archiving/batch/retry`（重新组批）

入参：`batchNo`。
出参：`sourceBatchNo, batchNo`（新批次）、`total, success, fail, items[]`（同 §4.7 明细）。
行为：取原批次 `FAIL` 明细 → 逐条重新校验 → 新建批次（记 `source_type='REBATCH'`、`source_ref=原批次号`）→ 返回新批次号。**原批次不改写**，审计留痕。重复调用会生成新批次，前端按返回刷新即可。
错误：`BATCH_NOT_FOUND`、`BATCH_NOT_RETRYABLE`（无失败项）。

### 4.9 `POST /api/mobile/archiving/location/list`

入参：`pageNum`、`pageSize`、`filter`（`ALL`|`PAPER`|`ELECTRONIC`|`MIXED`）、`keyword`、`level`（默认 `RACK`）。

`rows[]`：`locationId, locationCode, locationName, level, levelName, levelPath, house`（顶层库房名）、`capacity, used, unit, percent, status`（0 启用/1 停用）、`statusLabel`、`medium, mediumLabel, electronicPath`、`remark`、`archives[]`（`{archiveCode,title}`，最多 20 条）。

> 契约初稿里的 `note` 已删除：`arc_location` 没有这一列，页面只渲染真实存在的 `remark`（与 PC 同名字段一致）。

口径：

- `medium`/`electronicPath` 是**该库位下在架档案的聚合**（来自 `arc_archive.storage_media` / `electronic_location`），不是库位自身属性；全部纸质→`PAPER`，含电子与纸质→`MIXED`，只有电子→`ELECTRONIC`，该库位没有登记介质的档案→`""`（页面显示「未登记」）。与 PC 库位页同一口径。
- `used` = 在架档案数；`capacity` 为空表示不限；**容量本期不阻断归档**，只做占用展示（`percent` 由后端算好）。
- `unit`：纸质柜架「件」，电子节点「件」——统一用「件」，容量单位不做差异化。

## 5. 错误码表（业务失败统一 HTTP 400 + 字符串 code）

| code | msg（中文，可直接展示） | 触发场景 |
| --- | --- | --- |
| `ARCHIVE_NOT_FOUND` | 档案不存在或无权限访问 | 档号解析不到、越权、已删除（合并为一，避免探测） |
| `ARCHIVE_STATUS_CONFLICT` | 档案当前状态为{status}，只有「待归档」档案可以归档 | 状态不是 `SUBMITTED` |
| `RETENTION_UNRECOGNIZED` | 保管期限无法识别：{message}，请先补正期限 | `RETENTION` 项不通过 |
| `CHECK_NOT_PASSED` | 仍有检查项未通过：{label}（{detail}）；补正后才能归档 | 除期限外的其他检查项失败，`data.items` 带失败项 |
| `VERSION_CONFLICT` | 档案已被修改，请刷新后重试 | 乐观锁冲突 |
| `LOCATION_NOT_FOUND` | 库位不存在 | `locationId` 无效 |
| `LOCATION_DISABLED` | 库位已停用 | `arc_location.status=1` |
| `STORAGE_UNBOUND` | 存储介质为电子或混合时，必须填写电子存储位置 | 提交前校验，不写库 |
| `BATCH_NOT_FOUND` | 批次不存在 | |
| `BATCH_NOT_RETRYABLE` | 该批次没有可重新组批的失败项 | `fail_count=0` |
| `PARAM_INVALID` | 参数不合法：{field} | 分页越界、枚举非法、关键词超长 |

HTTP 状态码约定：401 登录失效、403 无权限、400 业务失败、503 小程序账号服务未接入、500 未预期异常（不再用字符串码占 `code` 判断 401/403，避免与业务码混淆）。401/403/503/500 的 `code` 字段为对应的数字状态码，只有 400 才放字符串业务码。

**相对草案的变化**：删除 `RETENTION_MISMATCH`（没有保管期限表/目录规则，属 M04 与阶段3）、`LOCATION_FULL`（本期不做容量阻断）、`BATCH_LOCKED`（没有审核态，改为 `BATCH_NOT_RETRYABLE`）；`RETENTION_UNRECOGNIZED`/`CHECK_NOT_PASSED`/`STORAGE_UNBOUND` 保留但文案带详情；新增 `ARCHIVE_STATUS_CONFLICT`、`VERSION_CONFLICT`、`LOCATION_NOT_FOUND`、`LOCATION_DISABLED`、`PARAM_INVALID`、`ARCHIVE_NOT_FOUND`、`BATCH_NOT_FOUND`、`BATCH_NOT_RETRYABLE`。

## 6. 校验报告未启用时的文案（PC 与小程序统一）

`arc_archive_check_report` 未建表时，后端在响应里带 `reportEnabled:false`，前端文案固定为：

> 校验报告未启用，请在数据库执行 sql/setup_qrs.sql 的 M10 段（arc_archive_check_report）后重试

不要再用「暂无校验报告」这种看起来像功能缺失的写法。

## 7. 阶段2 数据依赖与降级

阶段2 迁移已在 `sql/setup_qrs.sql` 落地（`-- M10阶段2归档属性迁移`、`-- M10阶段2批次来源迁移`），下列字段现在按真列返回。只有**未执行迁移的老库**才降级为 `""`，接口不报错、页面显示「未登记」。

| 接口字段 | 数据来源 | 列未迁移时 |
| --- | --- | --- |
| `medium` / `mediumLabel` | `arc_archive.storage_media` | `""`，前端显示「未登记」 |
| `electronicLocation` / `storageText` | `arc_archive.electronic_location` | `""` |
| `batchNo`（待归档/状态查询） | `arc_archive.archive_batch_id` → `arc_archive_batch.batch_no` | `""`（已归档时退回批次明细查原批次号） |
| `source` / `sourceDetail` | `arc_archive_batch.source_type` / `source_ref` | `""`、label「未登记」 |
| `archivedBy` | `arc_archive.archived_by` | `""` |

后端用 `information_schema.columns` 探测 4 个 `arc_archive` 阶段2 列（结果缓存 30 秒）决定读写语句：列齐→新语句，列缺→旧语句 + 空串降级，不会因为只升代码没迁移而整页报错。

## 8. 实现落点（已完成 2026-11-28）

- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/archive/MobileArchivingController.java`：9 个接口，`@RestController` + `@RequestMapping("/api/mobile/archiving")`，逐方法 `@Anonymous` + 手工校验 Bearer 令牌（与 `MobileArchiveSearchController` 同一套 `wx_token`/`wx_binding` 规则）。
- 复用 `ArchiveArchivingService`（`check`/`submit`/`saveCheckReport`/`latestReport`/`latestReports`/`hubSummary`/`mobileBatches`/`mobileBatchDetail`/`retryBatch`/`facts`/`retentionView`）与 `ArchiveLocationService`（`tree`/`storageByLocation`/`mediumLabel`）；未复制任何校验逻辑。
- 单件归档与小程序的「加入批次」都走 PC 的 `submit()`：来源记 `MOBILE`、介质与电子存储位置同一套校验（`STORAGE_UNBOUND` 在写库前抛出）。
- 移动端不提供导出/打印/库位维护（PC 专属）。

## 11. 实现偏差与补充说明

| 项 | 处理 |
| --- | --- |
| `batch/detail` 的 `checkReportId` | 不做新列：按 `arc_archive_check_report(batch_id, archive_id)` 反查该批次对该档案的 `SUBMIT` 报告；报告表未建时返回 `""` |
| `pending/list` 与 `status/search` 的期限筛选 | 期限要按字典标签判定，SQL 无法等价表达，实现为「上限 1000 条扫描 + 内存分页」，`total` 为筛选后真实条数；`hub/summary` 额外返回 `scanCapped` |
| `check` 响应 | 除契约字段外多返回 `message`（如「重新检查完成：全部通过」），与小程序桩层文案一致；`checkedAt` 为服务端当前时间 |
| `pending/list` 行字段 | 除契约字段外多返回 `locationCode`/`locationPath`/`archivedAt`/`archivedBy`/`checkedAt`/`archiveStatus`/`statusLabel`，与 §4.5 同义，便于两个页面共用渲染 |
| 批次计数命名 | 移动端用契约的 `total/success/fail`；PC 批次列表仍用 `totalCount/successCount/failCount`，同一数据 |
| `location/list` 的 `note` | 删除（无此列），只返回 `remark` |
| `batch/detail` 的 `items[]` | 2026-11-28 新增 `medium`/`mediumLabel`/`archivedBy`：PC 批次明细有「介质」「归档人」两列而接口没有，属两端不一致；已由 `batchItemFacts()` 统一供 PC 与小程序的同一份口径 |
| 阶段2 字段空值文案 | PC 与小程序统一「未登记」（不再用「—」）：归档列表的批次号/介质/电子存储位置/归档人四列、库位页电子存储位置、批次明细归档人 |

## 9. 与草案的逐条结论

| 草案 | 结论 |
| --- | --- |
| 8 个接口、路径 `/api/mobile/archiving/**` | 采纳，数量改 9：新增 `batch/detail`（批次明细必须单独取），`status/recheck` 并入通用 `check` |
| `status/recheck` 只刷新检查时间 | 修正：真写 CHECK 报告并返回逐项结果，否则「重新检查」没有可追溯记录 |
| 检查项 code（METADATA/READABLE/…） | 修正为后端 7 项 `STATUS/CODE/TITLE/CATEGORY/RETENTION/FILE/DESTROY`，label 由后端给 |
| 期限三态 PERMANENT/FIXED/UNKNOWN | 采纳，补 `years` 与 UNKNOWN 的 `message` |
| 错误码 6 个 | 修正为 §5 的 11 个业务码，删 3 个不存在的规则 |
| 批次 `returnedReason/returnedBy/returnedAt` | `returnedReason` 由失败明细聚合；`returnedBy/At` 本期无审核流，返回 `""` |
| 批次状态 RUNNING/REVIEW/RETURNED/DONE | 修正为真实 `RUNNING/SUCCESS/PARTIAL/FAILED`（REVIEW 需审核流，本期没有） |
| `LOCATION_FULL` 容量阻断 | 删除：本期容量只展示，不阻断 |
| 入口权限拟用 `draftPermissions.view` | 修正：复用 `archiveArchivingQuery` 等 PC 权限码 |
| 分页 `{rows,total,pageNum,pageSize,hasMore}` | 采纳，pageSize 上限 50 |
| 桩数据跨人（张敏/李强等） | 提醒：真实接口非管理员只见本人档案，联调时别按 mock 的 12 条期待 |

## 10. 待 Chief 拍板

1. **多件组批**：本期「加入批次」= 单件组批并归档。若要「多件陆续入池、统一提交」，需要批次草稿状态 + `archive_batch_id` 回填，属新增功能，影响 PC 与小程序两侧，需单独排期。
2. **批次审核/退回动作**：`REVIEW`、`returnedBy/returnedAt` 需要审批人、审核意见列与操作权限，属新流程；本期只按失败明细给「退回原因」，不做审核人留痕。
3. **库位容量阻断**：`LOCATION_FULL` 是否要拦归档，需先定「capacity 单位」（件/盒/GB 混用）与超限策略，本期只展示。
4. **接口数 8 → 9**：多出的是 `batch/detail`；若必须保持 8 个，可把它并进 `batch/list` 的 `batchNo` 精确查询，但不建议。
