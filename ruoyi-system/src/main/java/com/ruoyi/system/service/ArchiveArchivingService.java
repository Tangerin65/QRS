package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import com.github.pagehelper.PageHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final RowMapper<BatchRow> BATCH_ROW = (rs, i) -> new BatchRow(
        rs.getLong("batch_id"), rs.getString("batch_no"), rs.getString("applicant_name"),
        rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("fail_count"),
        rs.getString("batch_status"), rs.getString("submit_time"), rs.getString("finish_time"),
        rs.getObject("location_id", Long.class));

    private static final RowMapper<BatchItemRow> ITEM_ROW = (rs, i) -> new BatchItemRow(
        rs.getLong("item_id"), rs.getLong("batch_id"), rs.getLong("archive_id"),
        rs.getString("archive_code"), rs.getString("title"), rs.getString("item_result"),
        rs.getString("reason_code"), rs.getString("reason"));

    /** 待归档列表：沿用 M01 的数据范围与密级过滤，只取 SUBMITTED。 */
    public List<ArchiveRecord> pending(ArchiveQuery query) {
        query.setArchiveStatus(SUBMITTED);
        return archives.selectArchiveList(query);
    }

    public ArchiveRecord requireArchive(long archiveId, String actor, long actorId, boolean admin,
                                        LongPredicate categoryAllowed, int maxSecretLevel) {
        return archives.selectArchiveDetail(archiveId, actor, actorId, admin, categoryAllowed, maxSecretLevel);
    }

    /** 逐项校验：状态、档号、题名、分类末级、保管期限、附件、未结销毁。 */
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
        boolean retentionOk = retention.recognized()
            && (retention.permanent() || record.getFormingDate() != null);
        items.add(new CheckItem("RETENTION", "保管期限可计算", retentionOk,
            retentionOk ? retention.display() : "保管期限未设置或编码无法识别（值：" + nullSafe(record.getRetentionCode()) + "）"));
        long fileCount = countFiles(record.getArchiveId());
        items.add(new CheckItem("FILE", "至少一个附件", fileCount > 0, "附件 " + fileCount + " 个"));
        long openDestroy = countOpenDestroy(record.getArchiveId());
        items.add(new CheckItem("DESTROY", "无未结销毁申请", openDestroy == 0, "未结销毁单 " + openDestroy + " 条"));
        return items;
    }

    /** 保管期限识别结果：永久或有限年限。 */
    public record RetentionSpec(boolean recognized, boolean permanent, Integer years) {
        static final RetentionSpec UNKNOWN = new RetentionSpec(false, false, null);

        public String display() {
            if (permanent) return "永久";
            return years == null ? "" : years + " 年";
        }
    }

    /**
     * 识别保管期限编码。兼容两套字典键值：setup_qrs.sql 的 PERMANENT/Y30，
     * 与 arc_m01_dictionary.sql 的 1..10 数字键值；数字键值按字典标签换算，
     * 字典缺失时回退到登记时保存的名称快照。
     */
    public RetentionSpec resolveRetention(String retentionCode, String retentionName) {
        String code = retentionCode == null ? "" : retentionCode.trim();
        if (code.matches("\\d{1,3}")) {
            RetentionSpec fromDict = parseRetentionText(dictLabel(code), true);
            if (fromDict.recognized()) return fromDict;
            RetentionSpec fromName = parseRetentionText(retentionName, true);
            return fromName.recognized() ? fromName : RetentionSpec.UNKNOWN;
        }
        RetentionSpec direct = parseRetentionText(code, false);
        if (direct.recognized()) return direct;
        return parseRetentionText(retentionName, true);
    }

    /** 保管到期日：永久为空；有限年限按基准日加年。 */
    public LocalDate retentionEndDate(LocalDate baseDate, String retentionCode) {
        RetentionSpec spec = resolveRetention(retentionCode, null);
        return baseDate != null && spec.recognized() && !spec.permanent()
            ? baseDate.plusYears(spec.years()) : null;
    }

    private String dictLabel(String dictValue) {
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
        if (value.contains("永久")) return new RetentionSpec(true, true, null);
        String upper = value.toUpperCase();
        if ("PERMANENT".equals(upper) || "P".equals(upper)) return new RetentionSpec(true, true, null);
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("^Y?\\s*(\\d{1,3})\\s*年?$").matcher(upper);
        if (matcher.matches()) {
            if (!allowBareNumber && upper.matches("\\d{1,3}")) return RetentionSpec.UNKNOWN;
            int years = Integer.parseInt(matcher.group(1));
            if (years > 0) return new RetentionSpec(true, false, years);
        }
        return RetentionSpec.UNKNOWN;
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
                String reason = check(record, categoryAllowed).stream()
                    .filter(item -> !item.passed())
                    .map(item -> item.label() + "（" + item.detail() + "）")
                    .collect(Collectors.joining("；"));
                if (!reason.isEmpty()) {
                    fail++;
                    items.add(new SubmitItem(archiveId, record.getArchiveCode(), record.getTitle(), "FAIL", "CHECK", reason));
                    insertItem(batchId, record, "FAIL", "CHECK", reason);
                    continue;
                }
                RetentionSpec retention = resolveRetention(record.getRetentionCode(), record.getRetentionName());
                LocalDate endDate = retention.recognized() && !retention.permanent()
                    ? today.plusYears(retention.years()) : null;
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
