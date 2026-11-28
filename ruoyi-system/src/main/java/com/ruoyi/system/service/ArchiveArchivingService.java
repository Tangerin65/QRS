package com.ruoyi.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import com.github.pagehelper.PageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M10 归档与库位：待归档校验、批量归档、退回补正、批次台账。 */
@Service
public class ArchiveArchivingService {
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String ARCHIVED = "ARCHIVED";
    private static final int MAX_BATCH = 200;
    private static final Logger log = LoggerFactory.getLogger(ArchiveArchivingService.class);
    /** 校验报告逐项结果的 JSON 编解码；只用字符串与布尔，无 java.time 字段。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final IArchiveService archives;
    private final ArchiveCatalogService catalog;

    public ArchiveArchivingService(JdbcTemplate jdbc, IArchiveService archives, ArchiveCatalogService catalog) {
        this.jdbc = jdbc;
        this.archives = archives;
        this.catalog = catalog;
    }

    public record CheckItem(String code, String label, boolean passed, String detail) {}

    public record SubmitItem(long archiveId, String archiveCode, String title, String result, String reasonCode, String reason,
                             List<CheckItem> failedChecks) {}

    public record SubmitResult(long batchId, String batchNo, int total, int success, int fail, List<SubmitItem> items) {}

    public record BatchRow(long batchId, String batchNo, String applicantName, int totalCount, int successCount,
                           int failCount, String batchStatus, String submitTime, String finishTime, Long locationId,
                           String remark) {}

    public record BatchItemRow(long itemId, long batchId, long archiveId, String archiveCode, String title,
                               String itemResult, String reasonCode, String reason) {}

    /** 阶段2 归档属性：存储介质与电子存储位置，PC 与小程序同名同义。 */
    public record Storage(String media, String electronicLocation) {
        public static final Storage NONE = new Storage("", "");
    }

    /** 批次来源等阶段2 属性；列未迁移时各字段为空串。 */
    public record BatchFacts(String sourceType, String sourceRef, String locationCode) {}

    /**
     * 档案的阶段2 归档属性（批次号、介质、电子存储位置、归档人、库位路径）。
     * 列未迁移时除库位外都为空串，接口按契约降级而不是报错。
     */
    public record ArchiveFacts(long archiveId, String batchNo, String storageMedia, String electronicLocation,
                               String archivedBy, String archiveDate, Long locationId, String locationCode,
                               String locationPath) {}

    /** 带机器可读错误码的业务异常：PC 只用 msg，小程序额外用 code/data 映射契约错误码。 */
    public static class ArchivingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final Map<String, Object> data;

        public ArchivingException(String code, String message) {
            this(code, message, Map.of());
        }

        public ArchivingException(String code, String message, Map<String, Object> data) {
            super(message);
            this.code = code;
            this.data = data == null ? Map.of() : data;
        }

        public String code() { return code; }
        public Map<String, Object> data() { return data; }
    }

    /** 重新组批结果：原批次号 + 新批次。 */
    public record RetryResult(String sourceBatchNo, SubmitResult batch) {}

    private static final Set<String> MEDIA = Set.of("PAPER", "ELECTRONIC", "MIXED");
    private static final Map<String, String> MEDIA_LABELS = Map.of(
        "PAPER", "纸质", "ELECTRONIC", "电子", "MIXED", "混合");

    /** 单次校验报告：逐项结果与当时档案版本，归档前换版本即需复检。 */
    public record CheckReportRow(long reportId, long archiveId, String archiveCode, int archiveVersion, Long batchId,
                                 String reportSource, String reportResult, int itemTotal, int failCount,
                                 List<CheckItem> items, String checkedBy, String checkTime) {}

    private static final RowMapper<BatchRow> BATCH_ROW = (rs, i) -> new BatchRow(
        rs.getLong("batch_id"), rs.getString("batch_no"), rs.getString("applicant_name"),
        rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("fail_count"),
        rs.getString("batch_status"), rs.getString("submit_time"), rs.getString("finish_time"),
        rs.getObject("location_id", Long.class), rs.getString("remark"));

    private static final RowMapper<BatchItemRow> ITEM_ROW = (rs, i) -> new BatchItemRow(
        rs.getLong("item_id"), rs.getLong("batch_id"), rs.getLong("archive_id"),
        rs.getString("archive_code"), rs.getString("title"), rs.getString("item_result"),
        rs.getString("reason_code"), rs.getString("reason"));

    private static final RowMapper<CheckReportRow> REPORT_ROW = (rs, i) -> new CheckReportRow(
        rs.getLong("report_id"), rs.getLong("archive_id"), rs.getString("archive_code"),
        rs.getInt("archive_version"), rs.getObject("batch_id", Long.class), rs.getString("report_source"),
        rs.getString("report_result"), rs.getInt("item_total"), rs.getInt("fail_count"),
        parseItems(rs.getString("items_json")), rs.getString("checked_by"), rs.getString("check_time"));

    /** 待归档列表：沿用 M01 的数据范围与密级过滤，只取 SUBMITTED。 */
    public List<ArchiveRecord> pending(ArchiveQuery query) {
        query.setArchiveStatus(SUBMITTED);
        return archives.selectArchiveList(query);
    }

    /** 状态查询列表：SUBMITTED（默认）或 ARCHIVED，沿用同一套数据范围。 */
    public List<ArchiveRecord> list(ArchiveQuery query) {
        String status = query.getArchiveStatus();
        if (status == null || status.isBlank()) query.setArchiveStatus(SUBMITTED);
        return archives.selectArchiveList(query);
    }

    /** 不限归档状态的分页查询（小程序状态查询的 ALL），数据范围与 M01 一致。 */
    public List<ArchiveRecord> anyStatus(ArchiveQuery query) {
        return archives.selectArchiveList(query);
    }

    public ArchiveRecord requireArchive(long archiveId, String actor, long actorId, boolean admin,
                                        LongPredicate categoryAllowed, int maxSecretLevel) {
        return archives.selectArchiveDetail(archiveId, actor, actorId, admin, categoryAllowed, maxSecretLevel);
    }

    /** 逐项校验：状态、档号、题名、分类末级、保管期限、附件、未结销毁（共 7 项）。 */
    public List<CheckItem> check(ArchiveRecord record, LongPredicate categoryAllowed) {
        List<CheckItem> items = new ArrayList<>();
        boolean statusOk = SUBMITTED.equals(record.getArchiveStatus());
        items.add(new CheckItem("STATUS", "档案处于待归档状态", statusOk, statusOk ? "待归档" : "当前状态 " + record.getArchiveStatus()));
        boolean codeOk = record.getArchiveCode() != null && !record.getArchiveCode().isBlank();
        items.add(new CheckItem("CODE", "档号已生成", codeOk, codeOk ? record.getArchiveCode() : "档号为空"));
        boolean titleOk = record.getTitle() != null && !record.getTitle().isBlank();
        items.add(new CheckItem("TITLE", "题名完整", titleOk, titleOk ? "已填写" : "题名为空"));
        boolean categoryOk = false;
        String categoryDetail = "未设置分类";
        if (record.getCategoryId() != null) {
            categoryOk = catalog.registrationChoices("", categoryAllowed).stream()
                .anyMatch(choice -> choice.id() == record.getCategoryId());
            categoryDetail = categoryOk ? "启用末级分类" : "分类已停用或不是末级";
        }
        items.add(new CheckItem("CATEGORY", "分类为启用末级", categoryOk, categoryDetail));
        RetentionSpec retention = resolveRetention(record.getRetentionCode(), record.getRetentionName());
        items.add(new CheckItem("RETENTION", "保管期限可识别", retention.recognized(),
            retention.recognized() ? retention.display() : retention.message()));
        long fileCount = countFiles(record.getArchiveId());
        items.add(new CheckItem("FILE", "至少一个附件", fileCount > 0, "附件 " + fileCount + " 个"));
        long openDestroy = countOpenDestroy(record.getArchiveId());
        items.add(new CheckItem("DESTROY", "无未结销毁申请", openDestroy == 0, "未结销毁单 " + openDestroy + " 条"));
        return items;
    }

    /**
     * 保管期限识别结果。识别失败时 message 区分两种原因：编码不在字典里（数据/字典不同步），
     * 或字典标签解析不出年限（字典被改成不可解析的写法），避免统一报「无法识别」导致难定位。
     */
    public record RetentionSpec(boolean recognized, boolean permanent, Integer years, String message) {
        static final RetentionSpec UNKNOWN = new RetentionSpec(false, false, null, "无法识别");

        static RetentionSpec knownPermanent() {
            return new RetentionSpec(true, true, null, "");
        }

        static RetentionSpec knownYears(int years) {
            return new RetentionSpec(true, false, years, "");
        }

        static RetentionSpec unknown(String message) {
            return new RetentionSpec(false, false, null, message);
        }

        public String display() {
            if (permanent) return "永久";
            return years == null ? "" : years + " 年";
        }
    }

    /**
     * 识别保管期限。字典 arc_retention_period 是唯一真源，dict_value 只是键：
     * 先按 dict_value 取 dict_label 解析；键在字典里查不到时退回直接解析编码（兼容字典未初始化或被删改的机器），
     * 再退到登记时保存的 retention_name 快照。仍失败时给出可定位的原因。
     * 两套种子（setup_qrs 的 PERMANENT/Y30… 与 arc_m01_dictionary 的 1..10）都靠标签解析，因此都能识别。
     */
    public RetentionSpec resolveRetention(String retentionCode, String retentionName) {
        String code = retentionCode == null ? "" : retentionCode.trim();
        if (code.isEmpty()) return RetentionSpec.unknown("保管期限为空");
        String label = dictLabel(code);
        if (label != null) {
            RetentionSpec fromLabel = parseRetentionText(label, true);
            if (fromLabel.recognized()) return fromLabel;
            RetentionSpec fromName = parseRetentionText(retentionName, true);
            if (fromName.recognized()) return fromName;
            return RetentionSpec.unknown("字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 "
                + code + "，标签 " + label.trim() + "）");
        }
        RetentionSpec direct = parseRetentionText(code, false);
        if (direct.recognized()) return direct;
        RetentionSpec fromName = parseRetentionText(retentionName, true);
        if (fromName.recognized()) return fromName;
        return RetentionSpec.unknown("字典 arc_retention_period 中不存在该编码 " + code);
    }

    /** 保管到期日：永久为空；有限年限按基准日加年。 */
    public LocalDate retentionEndDate(LocalDate baseDate, String retentionCode) {
        RetentionSpec spec = resolveRetention(retentionCode, null);
        return baseDate != null && spec.recognized() && !spec.permanent()
            ? baseDate.plusYears(spec.years()) : null;
    }

    /** 字典是唯一真源。protected 仅为单测覆盖字典读取，业务代码不要覆盖。 */
    protected String dictLabel(String dictValue) {
        try {
            List<String> labels = jdbc.queryForList(
                "select dict_label from sys_dict_data where dict_type='arc_retention_period' and dict_value=? and status='0'",
                String.class, dictValue);
            return labels.isEmpty() ? null : labels.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static RetentionSpec parseRetentionText(String text, boolean allowBareNumber) {
        if (text == null) return RetentionSpec.UNKNOWN;
        String value = text.trim();
        if (value.isEmpty()) return RetentionSpec.UNKNOWN;
        if (value.contains("永久")) return RetentionSpec.knownPermanent();
        String upper = value.toUpperCase();
        if ("PERMANENT".equals(upper) || "P".equals(upper)) return RetentionSpec.knownPermanent();
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("^Y?\\s*(\\d{1,3})\\s*年?$").matcher(upper);
        if (matcher.matches()) {
            if (!allowBareNumber && upper.matches("\\d{1,3}")) return RetentionSpec.UNKNOWN;
            int years = Integer.parseInt(matcher.group(1));
            if (years > 0) return RetentionSpec.knownYears(years);
        }
        return RetentionSpec.UNKNOWN;
    }

    /**
     * 逐项校验结果落库，形成可追溯的校验报告。报告随档案版本保存：
     * 只对写入时的 archive.version 有效，档案被修改后必须重新校验。
     *
     * @param source CHECK 为人工校验，SUBMIT 为归档时的事务内复检
     */
    public void saveCheckReport(ArchiveRecord record, List<CheckItem> checks, Long batchId, String source,
                                String actor, long actorId) {
        int failCount = (int) checks.stream().filter(item -> !item.passed()).count();
        String result = failCount == 0 ? "PASS" : "FAIL";
        try {
            jdbc.update("insert into arc_archive_check_report(archive_id,archive_code,title,archive_version,batch_id,"
                    + "report_source,report_result,item_total,fail_count,items_json,checked_by_id,checked_by) "
                    + "values(?,?,?,?,?,?,?,?,?,?,?,?)",
                record.getArchiveId(), nullSafe(record.getArchiveCode()), nullSafe(record.getTitle()),
                record.getVersion() == null ? 0 : record.getVersion(), batchId, source, result,
                checks.size(), failCount, writeItems(checks), actorId, actor);
        } catch (DataAccessException e) {
            log.warn("校验报告未落库（请执行 sql/setup_qrs.sql 的 M10 校验报告建表段）：archiveId={}",
                record.getArchiveId(), e);
        }
    }

    /** 校验报告表是否已建；未建时前端提示「未启用」而不是「暂无报告」。 */
    public boolean reportEnabled() {
        try {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema=database() and table_name='arc_archive_check_report'", Integer.class);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    /** 最新一次校验报告；没有报告或未执行迁移时返回 null。 */
    public CheckReportRow latestReport(long archiveId) {
        List<CheckReportRow> rows = reports(archiveId, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 校验报告历史，新的在前。 */
    public List<CheckReportRow> reports(long archiveId, int limit) {
        try {
            return jdbc.query("select report_id,archive_id,archive_code,archive_version,batch_id,report_source,"
                + "report_result,item_total,fail_count,items_json,checked_by,"
                + "date_format(check_time,'%Y-%m-%d %H:%i:%s') check_time "
                + "from arc_archive_check_report where archive_id=? order by report_id desc limit ?",
                REPORT_ROW, archiveId, Math.max(1, Math.min(limit, 200)));
        } catch (DataAccessException e) {
            log.warn("读取校验报告失败（请执行 sql/setup_qrs.sql 的 M10 校验报告建表段）：archiveId={}", archiveId, e);
            return Collections.emptyList();
        }
    }

    /** 每条档案最新报告的结果，供待归档列表标记「待补正」。 */
    public Map<Long, String> latestCheckStates(List<Long> archiveIds) {        if (archiveIds == null || archiveIds.isEmpty()) return Collections.emptyMap();
        String placeholders = archiveIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, String> states = new HashMap<>();
        try {
            jdbc.query("select r.archive_id,r.report_result from arc_archive_check_report r "
                    + "join (select archive_id,max(report_id) report_id from arc_archive_check_report "
                    + "where archive_id in (" + placeholders + ") group by archive_id) t on r.report_id=t.report_id",
                (RowCallbackHandler) rs -> states.put(rs.getLong("archive_id"), rs.getString("report_result")),
                archiveIds.toArray());
        } catch (DataAccessException e) {
            log.warn("读取校验状态失败（请执行 sql/setup_qrs.sql 的 M10 校验报告建表段）：{}", e.getMessage());
        }
        return states;
    }

    /** 一批档案各自最新一份报告，键为 archive_id；列表页一次取出，避免逐条查库。 */
    public Map<Long, CheckReportRow> latestReports(List<Long> archiveIds) {
        if (archiveIds == null || archiveIds.isEmpty()) return Collections.emptyMap();
        String placeholders = archiveIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, CheckReportRow> reports = new HashMap<>();
        try {
            jdbc.query("select r.report_id,r.archive_id,r.archive_code,r.archive_version,r.batch_id,r.report_source,"
                    + "r.report_result,r.item_total,r.fail_count,r.items_json,r.checked_by,"
                    + "date_format(r.check_time,'%Y-%m-%d %H:%i:%s') check_time "
                    + "from arc_archive_check_report r join (select archive_id,max(report_id) report_id "
                    + "from arc_archive_check_report where archive_id in (" + placeholders + ") group by archive_id) t "
                    + "on r.report_id=t.report_id",
                (RowCallbackHandler) rs -> {
                    CheckReportRow row = REPORT_ROW.mapRow(rs, 0);
                    reports.put(row.archiveId(), row);
                }, archiveIds.toArray());
        } catch (DataAccessException e) {
            log.warn("批量读取校验报告失败（请执行 sql/setup_qrs.sql 的 M10 校验报告建表段）：{}", e.getMessage());
        }
        return reports;
    }

    /** 报告是否已过期：档案版本变了，上次校验结论不再适用，必须复检。 */
    public static boolean reportStale(CheckReportRow report, ArchiveRecord record) {
        return report != null && record != null && record.getVersion() != null
            && report.archiveVersion() != record.getVersion();
    }

    private static String writeItems(List<CheckItem> checks) {
        try {
            return JSON.writeValueAsString(checks);
        } catch (JsonProcessingException e) {
            throw new ServiceException("校验报告序列化失败");
        }
    }

    private static List<CheckItem> parseItems(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            CheckItem[] items = JSON.readValue(json, CheckItem[].class);
            return items == null ? Collections.emptyList() : List.of(items);
        } catch (JsonProcessingException e) {
            log.warn("校验报告 JSON 解析失败，按空报告处理", e);
            return Collections.emptyList();
        }
    }

    @Transactional
    public SubmitResult submit(List<Long> archiveIds, Long locationId, String actor, long actorId, boolean admin,
                               LongPredicate categoryAllowed, int maxSecretLevel) {
        // PC 手工勾选提交：批次来源记 MANUAL，介质与电子存储位置由弹窗采集。
        return submit(archiveIds, locationId, Storage.NONE, "MANUAL", "", actor, actorId, admin,
            categoryAllowed, maxSecretLevel);
    }

    /**
     * 批量归档。PC 与小程序同一条路径，只差来源与介质参数：
     * 小程序单件组批按 sourceType=MOBILE，重新组批按 REBATCH 并记原批次号。
     */
    @Transactional
    public SubmitResult submit(List<Long> archiveIds, Long locationId, Storage storage, String sourceType,
                               String sourceRef, String actor, long actorId, boolean admin,
                               LongPredicate categoryAllowed, int maxSecretLevel) {
        if (archiveIds == null || archiveIds.isEmpty()) throw new ServiceException("请选择待归档档案");
        if (archiveIds.size() > MAX_BATCH) throw new ServiceException("单批最多 " + MAX_BATCH + " 条");
        Storage media = requireStorage(storage);
        if (locationId != null) requireLocation(locationId);
        // 阶段2 列未迁移时退回旧语句，避免整批归档因列缺失失败。
        boolean stage2 = stage2Ready();
        LocalDate today = LocalDate.now();
        String batchNo = nextBatchNo();
        insertBatch(batchNo, actorId, actor, archiveIds.size(), locationId, stage2,
            sourceType == null ? "" : sourceType, sourceRef == null ? "" : sourceRef);
        Long batchId = jdbc.queryForObject("select batch_id from arc_archive_batch where batch_no=?", Long.class, batchNo);
        int ok = 0;
        int fail = 0;
        List<SubmitItem> items = new ArrayList<>();
        for (Long archiveId : archiveIds) {
            if (archiveId == null) continue;
            try {
                ArchiveRecord record = requireArchive(archiveId, actor, actorId, admin, categoryAllowed, maxSecretLevel);
                // 归档前一律在事务内复检，不复用列表页的旧报告：档案换版本后旧结论即刻失效。
                List<CheckItem> checks = check(record, categoryAllowed);
                List<CheckItem> failedChecks = checks.stream().filter(item -> !item.passed()).toList();
                saveCheckReport(record, checks, batchId, "SUBMIT", actor, actorId);
                if (!failedChecks.isEmpty()) {
                    String reason = failedChecks.stream()
                        .map(item -> item.label() + "（" + item.detail() + "）")
                        .collect(Collectors.joining("；"));
                    // 仅保管期限无法识别时单列 RETENTION，其余统一 CHECK；档案留在待归档待补正，不自动回草稿。
                    String reasonCode = failedChecks.size() == 1 && "RETENTION".equals(failedChecks.get(0).code())
                        ? "RETENTION" : "CHECK";
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", reasonCode, reason,
                        failedChecks));
                    insertItem(batchId, record, "FAIL", reasonCode, reason);
                    continue;
                }
                RetentionSpec retention = resolveRetention(record.getRetentionCode(), record.getRetentionName());
                if (!retention.recognized()) {
                    // 防御：RETENTION 校验项已拦截 UNKNOWN，走到这里说明校验项被改动过，仍拒绝归档。
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", "RETENTION",
                        retention.message(), checks));
                    insertItem(batchId, record, "FAIL", "RETENTION", retention.message());
                    continue;
                }
                LocalDate endDate = retention.permanent() ? null : today.plusYears(retention.years());
                int updated = stage2
                    ? jdbc.update("update arc_archive set archive_status='ARCHIVED',archive_date=?,retention_end_date=?,"
                        + "location_id=?,storage_media=?,electronic_location=?,archived_by=?,archive_batch_id=?,"
                        + "version=version+1,update_by=?,update_time=now() "
                        + "where archive_id=? and version=? and archive_status='SUBMITTED' and del_flag='0'",
                        today, endDate, locationId, media.media(), media.electronicLocation(), actor, batchId, actor,
                        archiveId, record.getVersion())
                    : jdbc.update("update arc_archive set archive_status='ARCHIVED',archive_date=?,retention_end_date=?,"
                        + "location_id=?,version=version+1,update_by=?,update_time=now() "
                        + "where archive_id=? and version=? and archive_status='SUBMITTED' and del_flag='0'",
                        today, endDate, locationId, actor, archiveId, record.getVersion());
                if (updated == 0) {
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", "CONFLICT",
                        "档案已被修改，刷新后重试", checks));
                    insertItem(batchId, record, "FAIL", "CONFLICT", "档案已被修改");
                    continue;
                }
                audit(archiveId, record.getArchiveCode(), "ARCHIVE_OK", actorId, actor,
                    "归档成功，批次 " + batchNo + (locationId == null ? "" : "，库位 " + locationId)
                        + (media.media().isEmpty() ? "" : "，介质 " + MEDIA_LABELS.get(media.media())));
                ok++;
                items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "OK", "", "", checks));
                insertItem(batchId, record, "OK", "", "");
            } catch (ServiceException e) {
                fail++;
                items.add(new SubmitItem(archiveId, "", "", "FAIL", "DENIED", e.getMessage(), List.of()));
            }
        }
        String status = fail == 0 ? "SUCCESS" : (ok == 0 ? "FAILED" : "PARTIAL");
        jdbc.update("update arc_archive_batch set success_count=?,fail_count=?,batch_status=?,finish_time=now() where batch_id=?",
            ok, fail, status, batchId);
        return new SubmitResult(batchId, batchNo, archiveIds.size(), ok, fail, items);
    }

    /** 待归档退回补正：回到草稿并记录原因。 */
    @Transactional
    public void returnToDraft(long archiveId, int version, String reason, String actor, long actorId, boolean admin,
                              LongPredicate categoryAllowed, int maxSecretLevel) {
        if (reason == null || reason.isBlank()) throw new ServiceException("退回原因必填");
        if (reason.length() > 200) throw new ServiceException("退回原因最长 200 字");
        ArchiveRecord record = requireArchive(archiveId, actor, actorId, admin, categoryAllowed, maxSecretLevel);
        if (!SUBMITTED.equals(record.getArchiveStatus())) throw new ServiceException("仅待归档档案可退回补正");
        int updated = jdbc.update("update arc_archive set archive_status='DRAFT',version=version+1,update_by=?,update_time=now() "
            + "where archive_id=? and version=? and archive_status='SUBMITTED' and del_flag='0'", actor, archiveId, version);
        if (updated == 0) throw new ServiceException("档案已被修改，刷新后重试");
        audit(archiveId, record.getArchiveCode(), "ARCHIVE_RETURN", actorId, actor, "退回补正：" + reason);
    }

    /** 归档统计：待归档、已归档、本月归档、失败批次。 */
    public Map<String, Object> statistics(ArchiveQuery query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", countArchive(query, SUBMITTED));
        result.put("archived", countArchive(query, ARCHIVED));
        result.put("monthArchived", jdbc.queryForObject("select count(*) from arc_archive where del_flag='0' and archive_status='ARCHIVED' "
            + "and archive_date >= date_format(curdate(),'%Y-%m-01')", Long.class));
        result.put("batchTotal", jdbc.queryForObject("select count(*) from arc_archive_batch", Long.class));
        result.put("batchPartial", jdbc.queryForObject("select count(*) from arc_archive_batch where batch_status in ('PARTIAL','FAILED')", Long.class));
        return result;
    }

    public Map<String, Object> listBatches(String keyword, String status, String from, String to, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (batch_no like ? or applicant_name like ?) ");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" and batch_status=? ");
            args.add(status.trim());
        }
        if (from != null && !from.isBlank()) {
            where.append(" and submit_time >= ? ");
            args.add(from.trim() + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            where.append(" and submit_time <= ? ");
            args.add(to.trim() + " 23:59:59");
        }
        Long total = jdbc.queryForObject("select count(*) from arc_archive_batch" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(Math.max(1, pageSize));
        pageArgs.add(Math.max(0, (Math.max(1, pageNum) - 1) * pageSize));
        List<BatchRow> rows = jdbc.query("select batch_id,batch_no,applicant_name,total_count,success_count,fail_count,"
            + "batch_status,date_format(submit_time,'%Y-%m-%d %H:%i:%s') submit_time,"
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id,remark from arc_archive_batch"
            + where + " order by batch_id desc limit ? offset ?", BATCH_ROW, pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    public BatchRow batchDetail(long batchId) {
        List<BatchRow> rows = jdbc.query("select batch_id,batch_no,applicant_name,total_count,success_count,fail_count,"
            + "batch_status,date_format(submit_time,'%Y-%m-%d %H:%i:%s') submit_time,"
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id,remark from arc_archive_batch where batch_id=?",
            BATCH_ROW, batchId);
        if (rows.isEmpty()) throw new ServiceException("批次不存在");
        return rows.get(0);
    }

    public List<BatchItemRow> batchItems(long batchId) {
        return jdbc.query("select item_id,batch_id,archive_id,archive_code,title,item_result,reason_code,reason "
            + "from arc_archive_batch_item where batch_id=? order by item_id", ITEM_ROW, batchId);
    }

    /** 小程序 hub 汇总（契约 §4.1）：待归档类指标按本人数据范围，批次与库位按全局口径。 */
    public Map<String, Object> hubSummary(ArchiveQuery query) {
        ArchiveQuery scoped = scopedCopy(query);
        scoped.setArchiveStatus(SUBMITTED);
        scoped.setMaxRows(HUB_SCAN);
        scoped.setPageNum(1);
        scoped.setPageSize(HUB_SCAN);
        List<ArchiveRecord> pendingRows = archives.selectArchiveList(scoped);
        Map<String, String> dict = retentionDict();
        Map<Long, CheckReportRow> reports = latestReports(pendingRows.stream().map(ArchiveRecord::getArchiveId).toList());
        int unknown = 0;
        int needFix = 0;
        for (ArchiveRecord record : pendingRows) {
            boolean unknownRetention = !resolveRetention(record.getRetentionCode(), record.getRetentionName(), dict)
                .recognized();
            // 口径与待归档列表的 NEED_FIX 筛选一致：期限无法识别，或最近一份报告失败且档案未换版本。
            CheckReportRow report = reports.get(record.getArchiveId());
            boolean failed = report != null && "FAIL".equals(report.reportResult()) && !reportStale(report, record);
            if (unknownRetention) unknown++;
            if (unknownRetention || failed) needFix++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pending", pendingRows.size());
        data.put("needFix", needFix);
        data.put("unknownRetention", unknown);
        data.put("batchOpen", count("select count(*) from arc_archive_batch where batch_status='RUNNING'"));
        data.put("batchReturned", count("select count(*) from arc_archive_batch where batch_status in ('PARTIAL','FAILED')"));
        data.put("locationTotal", count("select count(*) from arc_location where location_level='RACK'"));
        data.put("locationAvailable", count("select count(*) from arc_location l where l.location_level='RACK' "
            + "and l.status=0 and (l.capacity is null or (select count(*) from arc_archive a "
            + "where a.location_id=l.location_id and a.del_flag='0') < l.capacity)"));
        data.put("archivedThisMonth", count("select count(*) from arc_archive where del_flag='0' "
            + "and archive_status='ARCHIVED' and archive_date >= date_format(curdate(),'%Y-%m-01')"));
        data.put("scanCapped", pendingRows.size() >= HUB_SCAN);
        return data;
    }

    /** 小程序批次列表（契约 §4.6）：筛选 ALL/OPEN/RETURNED，返回契约字段名。 */
    public Map<String, Object> mobileBatches(String keyword, String filter, String from, String to,
                                             int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (batch_no like ? or applicant_name like ?) ");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if ("OPEN".equalsIgnoreCase(filter)) {
            where.append(" and batch_status='RUNNING' ");
        } else if ("RETURNED".equalsIgnoreCase(filter)) {
            where.append(" and batch_status in ('PARTIAL','FAILED') ");
        }
        if (from != null && !from.isBlank()) {
            where.append(" and submit_time >= ? ");
            args.add(from.trim() + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            where.append(" and submit_time <= ? ");
            args.add(to.trim() + " 23:59:59");
        }
        int size = Math.max(1, Math.min(50, pageSize));
        int page = Math.max(1, pageNum);
        long total = count("select count(*) from arc_archive_batch" + where, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<BatchRow> rows = jdbc.query("select batch_id,batch_no,applicant_name,total_count,success_count,fail_count,"
            + "batch_status,date_format(submit_time,'%Y-%m-%d %H:%i:%s') submit_time,"
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id,remark from arc_archive_batch"
            + where + " order by batch_id desc limit ? offset ?", BATCH_ROW, pageArgs.toArray());
        List<Long> ids = rows.stream().map(BatchRow::batchId).toList();
        Map<Long, BatchFacts> facts = batchFacts(ids);
        Map<Long, String> reasons = batchReturnedReasons(ids);
        Map<Long, String> retentions = batchRetentionText(ids);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows.stream()
            .map(row -> mobileBatchView(row, facts.get(row.batchId()), reasons.get(row.batchId()),
                retentions.get(row.batchId())))
            .toList());
        data.put("total", total);
        data.put("pageNum", page);
        data.put("pageSize", size);
        data.put("hasMore", (long) page * size < total);
        return data;
    }

    /**
     * 批次明细的归档属性（介质、电子存储位置、归档人、库位编码），PC 与小程序共用同一份口径。
     * 底层就是 facts()，阶段2 列未迁移时除库位外为空串。
     */
    public Map<Long, ArchiveFacts> batchItemFacts(long batchId) {
        return facts(batchItems(batchId).stream().map(BatchItemRow::archiveId).toList());
    }

    /** 小程序批次详情（契约 §4.7）：batch + items（含介质、归档人与关联校验报告 ID）。 */
    public Map<String, Object> mobileBatchDetail(String batchNo) {
        BatchRow row = batchByNo(batchNo);
        Map<Long, BatchFacts> facts = batchFacts(List.of(row.batchId()));
        Map<Long, String> reasons = batchReturnedReasons(List.of(row.batchId()));
        Map<Long, String> retentions = batchRetentionText(List.of(row.batchId()));
        List<BatchItemRow> itemRows = batchItems(row.batchId());
        Map<Long, ArchiveFacts> itemFacts = facts(itemRows.stream().map(BatchItemRow::archiveId).toList());
        Map<Long, Long> reports = new HashMap<>();
        try {
            jdbc.query("select archive_id,max(report_id) report_id from arc_archive_check_report where batch_id=? "
                    + "group by archive_id",
                (RowCallbackHandler) rs -> reports.put(rs.getLong("archive_id"), rs.getLong("report_id")), row.batchId());
        } catch (DataAccessException e) {
            // 报告表未建：明细不带 checkReportId，其余照常返回。
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (BatchItemRow item : itemRows) {
            ArchiveFacts archiveFacts = itemFacts.get(item.archiveId());
            String media = archiveFacts == null ? "" : archiveFacts.storageMedia();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("archiveId", Long.toString(item.archiveId()));
            view.put("archiveCode", nullSafe(item.archiveCode()));
            view.put("title", nullSafe(item.title()));
            view.put("itemResult", nullSafe(item.itemResult()));
            view.put("reasonCode", nullSafe(item.reasonCode()));
            view.put("reason", nullSafe(item.reason()));
            view.put("medium", media);
            view.put("mediumLabel", mediumLabel(media));
            view.put("archivedBy", archiveFacts == null ? "" : archiveFacts.archivedBy());
            Long reportId = reports.get(item.archiveId());
            view.put("checkReportId", reportId == null ? "" : Long.toString(reportId));
            items.add(view);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batch", mobileBatchView(row, facts.get(row.batchId()), reasons.get(row.batchId()),
            retentions.get(row.batchId())));
        data.put("items", items);
        return data;
    }

    /**
     * 重新组批（契约 §4.8）：取原批次 FAIL 明细逐条重新校验，写新批次并记 source_type=REBATCH、
     * source_ref=原批次号；原批次不改写。
     */
    @Transactional
    public RetryResult retryBatch(String batchNo, String actor, long actorId, boolean admin,
                                  LongPredicate categoryAllowed, int maxSecretLevel) {
        BatchRow row = batchByNo(batchNo);
        List<Long> failIds = jdbc.queryForList("select archive_id from arc_archive_batch_item "
            + "where batch_id=? and item_result='FAIL' order by item_id", Long.class, row.batchId());
        if (failIds.isEmpty()) {
            throw new ArchivingException("BATCH_NOT_RETRYABLE", "该批次没有可重新组批的失败项");
        }
        SubmitResult submitted = submit(failIds, row.locationId(), Storage.NONE, "REBATCH", row.batchNo(), actor,
            actorId, admin, categoryAllowed, maxSecretLevel);
        return new RetryResult(row.batchNo(), submitted);
    }

    private BatchRow batchByNo(String batchNo) {
        List<BatchRow> rows = jdbc.query("select batch_id,batch_no,applicant_name,total_count,success_count,fail_count,"
            + "batch_status,date_format(submit_time,'%Y-%m-%d %H:%i:%s') submit_time,"
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id,remark from arc_archive_batch "
            + "where batch_no=?", BATCH_ROW, batchNo == null ? "" : batchNo.trim());
        if (rows.isEmpty()) throw new ArchivingException("BATCH_NOT_FOUND", "批次不存在");
        return rows.get(0);
    }


    /** 批次失败明细聚合，作为「退回原因」展示；无失败为空串。 */
    private Map<Long, String> batchReturnedReasons(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return Collections.emptyMap();
        String placeholders = batchIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, String> reasons = new HashMap<>();
        jdbc.query("select batch_id,group_concat(distinct reason separator '；') reasons from arc_archive_batch_item "
                + "where item_result='FAIL' and batch_id in (" + placeholders + ") group by batch_id",
            (RowCallbackHandler) rs -> reasons.put(rs.getLong("batch_id"), limit(rs.getString("reasons"), 200)),
            batchIds.toArray());
        return reasons;
    }

    private Map<String, Object> mobileBatchView(BatchRow row, BatchFacts facts, String returnedReason,
                                                String retentionText) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("batchId", Long.toString(row.batchId()));
        view.put("batchNo", nullSafe(row.batchNo()));
        String remark = nullSafe(row.remark());
        view.put("name", !remark.isBlank() ? remark
            : nullSafe(row.applicantName()) + " " + datePart(row.submitTime()) + " 归档批次");
        String sourceType = facts == null ? "" : nullSafe(facts.sourceType());
        view.put("source", sourceType);
        view.put("sourceLabel", sourceLabel(sourceType));
        view.put("sourceDetail", "REBATCH".equals(sourceType) && facts != null && !facts.sourceRef().isBlank()
            ? "原批次 " + facts.sourceRef() : "");
        view.put("total", row.totalCount());
        view.put("success", row.successCount());
        view.put("fail", row.failCount());
        view.put("retentionText", retentionText == null ? "" : retentionText);
        view.put("status", nullSafe(row.batchStatus()));
        view.put("statusLabel", batchStatusLabel(row.batchStatus()));
        view.put("applicantName", nullSafe(row.applicantName()));
        view.put("locationCode", facts == null ? "" : nullSafe(facts.locationCode()));
        view.put("createdAt", nullSafe(row.submitTime()));
        view.put("updatedAt", nullSafe(row.finishTime()));
        view.put("canRetry", row.failCount() > 0);
        view.put("returnedReason", returnedReason == null ? "" : returnedReason);
        view.put("returnedBy", "");
        view.put("returnedAt", "");
        return view;
    }

    public static String batchStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "RUNNING" -> "进行中";
            case "SUCCESS" -> "已完成";
            case "PARTIAL" -> "部分失败";
            case "FAILED" -> "全部失败";
            default -> nullSafe(status);
        };
    }

    public static String sourceLabel(String sourceType) {
        return switch (sourceType == null ? "" : sourceType) {
            case "MANUAL" -> "手工归档";
            case "MOBILE" -> "小程序归档";
            case "REBATCH" -> "重新组批";
            case "TRANSFER" -> "移交入库";
            case "AUTO" -> "自动归档";
            default -> "未登记";
        };
    }

    /** 待归档筛选与统计的扫描上限：NEED_FIX/UNKNOWN 需逐条判定期限，SQL 无法等价表达。 */
    public static final int HUB_SCAN = 1000;

    private static String datePart(String datetime) {
        return datetime == null || datetime.length() < 10 ? "" : datetime.substring(0, 10);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static ArchiveQuery scopedCopy(ArchiveQuery query) {
        ArchiveQuery scoped = new ArchiveQuery();
        scoped.setOwnerName(query.getOwnerName());
        scoped.setAdmin(query.isAdmin());
        scoped.setAllowedCategoryIds(query.getAllowedCategoryIds());
        scoped.setMaxSecretLevel(query.getMaxSecretLevel());
        return scoped;
    }

    /** 按当前用户的分类/密级范围统计某状态数量。 */
    private long countArchive(ArchiveQuery query, String status) {
        ArchiveQuery scoped = new ArchiveQuery();
        scoped.setOwnerName(query.getOwnerName());
        scoped.setAdmin(query.isAdmin());
        scoped.setAllowedCategoryIds(query.getAllowedCategoryIds());
        scoped.setMaxSecretLevel(query.getMaxSecretLevel());
        scoped.setArchiveStatus(status);
        PageHelper.startPage(1, 1);
        List<ArchiveRecord> rows = archives.selectArchiveList(scoped);
        return rows instanceof com.github.pagehelper.Page<?> page ? page.getTotal() : rows.size();
    }

    private long countFiles(long archiveId) {
        Long count = jdbc.queryForObject("select count(*) from arc_file where archive_id=?", Long.class, archiveId);
        return count == null ? 0 : count;
    }

    private long countOpenDestroy(long archiveId) {
        try {
            Long count = jdbc.queryForObject("select count(*) from arc_destroy where archive_id=? "
                + "and status in ('SUBMITTED','APPROVING','APPROVED')", Long.class, archiveId);
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private void requireLocation(long locationId) {
        List<Integer> rows = jdbc.queryForList("select status from arc_location where location_id=?", Integer.class, locationId);
        if (rows.isEmpty()) throw new ArchivingException("LOCATION_NOT_FOUND", "库位不存在");
        if (rows.get(0) != 0) throw new ArchivingException("LOCATION_DISABLED", "库位已停用");
    }

    /** 校验并规范化存储介质：可选（空表示未登记），电子/混合必须给电子存储位置。 */
    private Storage requireStorage(Storage storage) {
        if (storage == null) return Storage.NONE;
        String media = storage.media() == null ? "" : storage.media().trim().toUpperCase();
        String electronic = storage.electronicLocation() == null ? "" : storage.electronicLocation().trim();
        if (!media.isEmpty() && !MEDIA.contains(media)) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：storageMedia", Map.of("field", "storageMedia"));
        }
        if (("ELECTRONIC".equals(media) || "MIXED".equals(media)) && electronic.isEmpty()) {
            throw new ArchivingException("STORAGE_UNBOUND", "存储介质为电子或混合时，必须填写电子存储位置");
        }
        if (electronic.length() > 500) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：electronicLocation", Map.of("field", "electronicLocation"));
        }
        return new Storage(media, electronic);
    }

    public static String mediumLabel(String media) {
        return media == null || media.isEmpty() ? "未登记" : MEDIA_LABELS.getOrDefault(media, media);
    }

    private void insertBatch(String batchNo, long applicantId, String applicantName, int total, Long locationId,
                             boolean stage2, String sourceType, String sourceRef) {
        if (stage2) {
            jdbc.update("insert into arc_archive_batch(batch_no,applicant_id,applicant_name,total_count,batch_status,"
                    + "location_id,source_type,source_ref) values(?,?,?,?,'RUNNING',?,?,?)",
                batchNo, applicantId, applicantName, total, locationId, sourceType, sourceRef);
            return;
        }
        jdbc.update("insert into arc_archive_batch(batch_no,applicant_id,applicant_name,total_count,batch_status,location_id) "
            + "values(?,?,?,?,'RUNNING',?)", batchNo, applicantId, applicantName, total, locationId);
    }

    /**
     * 阶段2 列是否已迁移（storage_media/electronic_location/archived_by/archive_batch_id）。
     * 没迁移时归档与查询退回旧语句，页面按契约显示「未登记」而不是整页报错；结果缓存 30 秒。
     */
    public boolean stage2Ready() {
        long now = System.currentTimeMillis();
        if (now - stage2CheckedAt < 30_000L) return stage2Columns;
        boolean ready;
        try {
            Integer count = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() "
                + "and table_name='arc_archive' and column_name in "
                + "('storage_media','electronic_location','archived_by','archive_batch_id')", Integer.class);
            ready = count != null && count >= 4;
        } catch (DataAccessException e) {
            ready = false;
        }
        stage2Columns = ready;
        stage2CheckedAt = now;
        return ready;
    }

    private volatile long stage2CheckedAt = 0L;
    private volatile boolean stage2Columns = false;

    /** 一批档案的阶段2 归档属性，键为 archive_id；列未迁移时除库位外均为空串。 */
    public Map<Long, ArchiveFacts> facts(List<Long> archiveIds) {
        if (archiveIds == null || archiveIds.isEmpty()) return Collections.emptyMap();
        boolean stage2 = stage2Ready();
        String placeholders = archiveIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String extra = stage2
            ? "a.archive_batch_id,a.storage_media,a.electronic_location,a.archived_by,b.batch_no,"
            : "null archive_batch_id,'' storage_media,'' electronic_location,'' archived_by,'' batch_no,";
        String batchJoin = stage2 ? "left join arc_archive_batch b on b.batch_id=a.archive_batch_id " : "";
        String sql = "select a.archive_id," + extra + "date_format(a.archive_date,'%Y-%m-%d') archive_date,"
            + "a.location_id,l.location_code,"
            + "concat_ws(' / ', gw.location_code, ga.location_code, p.location_code, l.location_code) location_path "
            + "from arc_archive a left join arc_location l on l.location_id=a.location_id "
            + "left join arc_location p on p.location_id=l.parent_id "
            + "left join arc_location ga on ga.location_id=p.parent_id "
            + "left join arc_location gw on gw.location_id=ga.parent_id " + batchJoin
            + "where a.archive_id in (" + placeholders + ")";
        Map<Long, ArchiveFacts> facts = new HashMap<>();
        try {
            jdbc.query(sql, (RowCallbackHandler) rs -> {
                long id = rs.getLong("archive_id");
                facts.put(id, new ArchiveFacts(id, nullSafe(rs.getString("batch_no")),
                    nullSafe(rs.getString("storage_media")), nullSafe(rs.getString("electronic_location")),
                    nullSafe(rs.getString("archived_by")), nullSafe(rs.getString("archive_date")),
                    rs.getObject("location_id", Long.class), nullSafe(rs.getString("location_code")),
                    nullSafe(rs.getString("location_path"))));
            }, archiveIds.toArray());
        } catch (DataAccessException e) {
            log.warn("读取归档属性失败（请执行 sql/setup_qrs.sql 的 M10 阶段2 迁移段）：{}", e.getMessage());
        }
        return facts;
    }

    /** 批次的阶段2 属性（来源、来源引用、库位编码），键为 batch_id；来源两列未迁移时为空串。 */
    public Map<Long, BatchFacts> batchFacts(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return Collections.emptyMap();
        String placeholders = batchIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String extra = stage2Ready() ? "b.source_type,b.source_ref," : "'' source_type,'' source_ref,";
        Map<Long, BatchFacts> facts = new HashMap<>();
        try {
            jdbc.query("select b.batch_id," + extra
                    + "(select l.location_code from arc_location l where l.location_id=b.location_id) location_code "
                    + "from arc_archive_batch b where b.batch_id in (" + placeholders + ")",
                (RowCallbackHandler) rs -> facts.put(rs.getLong("batch_id"),
                    new BatchFacts(nullSafe(rs.getString("source_type")), nullSafe(rs.getString("source_ref")),
                        nullSafe(rs.getString("location_code")))), batchIds.toArray());
        } catch (DataAccessException e) {
            log.warn("读取批次来源失败（请执行 sql/setup_qrs.sql 的 M10 阶段2 迁移段）：{}", e.getMessage());
        }
        return facts;
    }

    /** 批次内保管期限分布文案，如「永久 3 / 30年 7」；无档案或列未迁移时为空串。 */
    public Map<Long, String> batchRetentionText(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return Collections.emptyMap();
        String placeholders = batchIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, Map<String, Integer>> counts = new LinkedHashMap<>();
        Map<Long, List<String>> order = new LinkedHashMap<>();
        Map<String, String> dict = retentionDict();
        try {
            jdbc.query("select i.batch_id,a.retention_code,a.retention_name,count(*) total "
                    + "from arc_archive_batch_item i join arc_archive a on a.archive_id=i.archive_id "
                    + "where i.batch_id in (" + placeholders + ") "
                    + "group by i.batch_id,a.retention_code,a.retention_name order by i.batch_id,count(*) desc",
                (RowCallbackHandler) rs -> {
                    long batchId = rs.getLong("batch_id");
                    RetentionSpec spec = resolveRetention(rs.getString("retention_code"),
                        rs.getString("retention_name"), dict);
                    String label = spec.recognized() ? spec.display() : "无法识别";
                    counts.computeIfAbsent(batchId, key -> new LinkedHashMap<>())
                        .merge(label, rs.getInt("total"), Integer::sum);
                    order.computeIfAbsent(batchId, key -> new ArrayList<>());
                    if (!order.get(batchId).contains(label)) order.get(batchId).add(label);
                }, batchIds.toArray());
        } catch (DataAccessException e) {
            return Collections.emptyMap();
        }
        Map<Long, String> texts = new LinkedHashMap<>();
        counts.forEach((batchId, byLabel) -> texts.put(batchId, order.get(batchId).stream()
            .map(label -> label + " " + byLabel.get(label)).collect(Collectors.joining(" / "))));
        return texts;
    }

    /** 字典 arc_retention_period 全量（编码 → 标签），列表类接口一次读出后内存判定。 */
    public Map<String, String> retentionDict() {
        Map<String, String> dict = new LinkedHashMap<>();
        try {
            jdbc.query("select dict_value,dict_label from sys_dict_data where dict_type='arc_retention_period' and status='0'",
                (RowCallbackHandler) rs -> dict.put(rs.getString("dict_value"), rs.getString("dict_label")));
        } catch (DataAccessException e) {
            return Collections.emptyMap();
        }
        return dict;
    }

    /** 契约 §3 的期限三态：code/label/state/years/message。 */
    public Map<String, Object> retentionView(ArchiveRecord record, Map<String, String> dict) {
        RetentionSpec spec = resolveRetention(record.getRetentionCode(), record.getRetentionName(), dict);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", nullSafe(record.getRetentionCode()));
        view.put("label", spec.recognized() ? spec.display()
            : (record.getRetentionName() == null || record.getRetentionName().isBlank()
                ? "无法识别" : record.getRetentionName()));
        view.put("state", !spec.recognized() ? "UNKNOWN" : (spec.permanent() ? "PERMANENT" : "FIXED"));
        view.put("years", spec.recognized() && !spec.permanent() ? spec.years() : null);
        view.put("message", spec.recognized() ? "" : spec.message());
        return view;
    }

    /** 用整份字典判定保管期限，避免列表逐条查库。 */
    public RetentionSpec resolveRetention(String retentionCode, String retentionName, Map<String, String> dict) {
        if (dict == null || dict.isEmpty()) return resolveRetention(retentionCode, retentionName);
        String code = retentionCode == null ? "" : retentionCode.trim();
        if (code.isEmpty()) return RetentionSpec.unknown("保管期限为空");
        String label = dict.get(code);
        if (label != null) {
            RetentionSpec fromLabel = parseRetentionText(label, true);
            if (fromLabel.recognized()) return fromLabel;
            RetentionSpec fromName = parseRetentionText(retentionName, true);
            if (fromName.recognized()) return fromName;
            return RetentionSpec.unknown("字典标签无法解析，应为「永久」或阿拉伯数字+年（编码 "
                + code + "，标签 " + label.trim() + "）");
        }
        RetentionSpec direct = parseRetentionText(code, false);
        if (direct.recognized()) return direct;
        RetentionSpec fromName = parseRetentionText(retentionName, true);
        if (fromName.recognized()) return fromName;
        return RetentionSpec.unknown("字典 arc_retention_period 中不存在该编码 " + code);
    }

    private void insertItem(Long batchId, ArchiveRecord record, String result, String reasonCode, String reason) {
        jdbc.update("insert into arc_archive_batch_item(batch_id,archive_id,archive_code,title,item_result,reason_code,reason) "
            + "values(?,?,?,?,?,?,?)", batchId, record.getArchiveId(), nullSafe(record.getArchiveCode()),
            nullSafe(record.getTitle()), result, reasonCode, limit(reason, 200));
    }

    private void audit(long archiveId, String archiveCode, String eventType, long actorId, String actorName, String detail) {
        jdbc.update("insert into arc_audit_event(archive_id,archive_code,event_type,actor_id,actor_name,detail) "
            + "values(?,?,?,?,?,?)", archiveId, archiveCode, eventType, actorId, actorName, limit(detail, 500));
    }

    private String nextBatchNo() {
        return "GD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
