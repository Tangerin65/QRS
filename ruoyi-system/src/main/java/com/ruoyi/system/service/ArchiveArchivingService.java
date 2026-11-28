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

    public record SubmitItem(long archiveId, String archiveCode, String title, String result, String reasonCode, String reason) {}

    public record SubmitResult(long batchId, String batchNo, int total, int success, int fail, List<SubmitItem> items) {}

    public record BatchRow(long batchId, String batchNo, String applicantName, int totalCount, int successCount,
                           int failCount, String batchStatus, String submitTime, String finishTime, Long locationId) {}

    public record BatchItemRow(long itemId, long batchId, long archiveId, String archiveCode, String title,
                               String itemResult, String reasonCode, String reason) {}

    /** 单次校验报告：逐项结果与当时档案版本，归档前换版本即需复检。 */
    public record CheckReportRow(long reportId, long archiveId, String archiveCode, int archiveVersion, Long batchId,
                                 String reportSource, String reportResult, int itemTotal, int failCount,
                                 List<CheckItem> items, String checkedBy, String checkTime) {}

    private static final RowMapper<BatchRow> BATCH_ROW = (rs, i) -> new BatchRow(
        rs.getLong("batch_id"), rs.getString("batch_no"), rs.getString("applicant_name"),
        rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("fail_count"),
        rs.getString("batch_status"), rs.getString("submit_time"), rs.getString("finish_time"),
        rs.getObject("location_id", Long.class));

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
    public Map<Long, String> latestCheckStates(List<Long> archiveIds) {
        if (archiveIds == null || archiveIds.isEmpty()) return Collections.emptyMap();
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
        if (archiveIds == null || archiveIds.isEmpty()) throw new ServiceException("请选择待归档档案");
        if (archiveIds.size() > MAX_BATCH) throw new ServiceException("单批最多 " + MAX_BATCH + " 条");
        if (locationId != null) requireLocation(locationId);
        LocalDate today = LocalDate.now();
        String batchNo = nextBatchNo();
        jdbc.update("insert into arc_archive_batch(batch_no,applicant_id,applicant_name,total_count,batch_status,location_id) "
            + "values(?,?,?,?,'RUNNING',?)", batchNo, actorId, actor, archiveIds.size(), locationId);
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
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", reasonCode, reason));
                    insertItem(batchId, record, "FAIL", reasonCode, reason);
                    continue;
                }
                RetentionSpec retention = resolveRetention(record.getRetentionCode(), record.getRetentionName());
                if (!retention.recognized()) {
                    // 防御：RETENTION 校验项已拦截 UNKNOWN，走到这里说明校验项被改动过，仍拒绝归档。
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", "RETENTION",
                        retention.message()));
                    insertItem(batchId, record, "FAIL", "RETENTION", retention.message());
                    continue;
                }
                LocalDate endDate = retention.permanent() ? null : today.plusYears(retention.years());
                int updated = jdbc.update("update arc_archive set archive_status='ARCHIVED',archive_date=?,retention_end_date=?,"
                        + "location_id=?,version=version+1,update_by=?,update_time=now() "
                        + "where archive_id=? and version=? and archive_status='SUBMITTED' and del_flag='0'",
                    today, endDate, locationId, actor, archiveId, record.getVersion());
                if (updated == 0) {
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", "CONFLICT", "档案已被修改，刷新后重试"));
                    insertItem(batchId, record, "FAIL", "CONFLICT", "档案已被修改");
                    continue;
                }
                audit(archiveId, record.getArchiveCode(), "ARCHIVE_OK", actorId, actor,
                    "归档成功，批次 " + batchNo + (locationId == null ? "" : "，库位 " + locationId));
                ok++;
                items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "OK", "", ""));
                insertItem(batchId, record, "OK", "", "");
            } catch (ServiceException e) {
                fail++;
                items.add(new SubmitItem(archiveId, "", "", "FAIL", "DENIED", e.getMessage()));
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
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id from arc_archive_batch"
            + where + " order by batch_id desc limit ? offset ?", BATCH_ROW, pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    public BatchRow batchDetail(long batchId) {
        List<BatchRow> rows = jdbc.query("select batch_id,batch_no,applicant_name,total_count,success_count,fail_count,"
            + "batch_status,date_format(submit_time,'%Y-%m-%d %H:%i:%s') submit_time,"
            + "date_format(finish_time,'%Y-%m-%d %H:%i:%s') finish_time,location_id from arc_archive_batch where batch_id=?",
            BATCH_ROW, batchId);
        if (rows.isEmpty()) throw new ServiceException("批次不存在");
        return rows.get(0);
    }

    public List<BatchItemRow> batchItems(long batchId) {
        return jdbc.query("select item_id,batch_id,archive_id,archive_code,title,item_result,reason_code,reason "
            + "from arc_archive_batch_item where batch_id=? order by item_id", ITEM_ROW, batchId);
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
        if (rows.isEmpty()) throw new ServiceException("库位不存在");
        if (rows.get(0) != 0) throw new ServiceException("库位已停用");
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
