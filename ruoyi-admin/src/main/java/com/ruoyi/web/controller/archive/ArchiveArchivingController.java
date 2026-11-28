package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveArchivingService;
import com.ruoyi.system.service.ArchiveArchivingService.ArchiveFacts;
import com.ruoyi.system.service.ArchiveArchivingService.BatchFacts;
import com.ruoyi.system.service.ArchiveArchivingService.BatchItemRow;
import com.ruoyi.system.service.ArchiveArchivingService.BatchRow;
import com.ruoyi.system.service.ArchiveArchivingService.CheckItem;
import com.ruoyi.system.service.ArchiveArchivingService.CheckReportRow;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveCatalogService.Choice;
import com.ruoyi.system.service.ArchiveSecretLevels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** M10 档案归档：待归档列表、校验、批量归档、退回补正、批次台账。 */
@Controller
@RequestMapping("/records")
public class ArchiveArchivingController extends BaseController {
    private static final String QUERY = "archiveArchivingQuery";
    private static final String SUBMIT = "archiveArchivingSubmit";
    private static final String RETURN = "archiveArchivingReturn";
    private static final String BATCH_QUERY = "archiveBatchQuery";

    private final ArchiveArchivingService archiving;
    private final ArchiveCatalogService catalog;
    private final ArchiveSecretLevels secretLevels;

    public ArchiveArchivingController(ArchiveArchivingService archiving, ArchiveCatalogService catalog,
                                      ArchiveSecretLevels secretLevels) {
        this.archiving = archiving;
        this.catalog = catalog;
        this.secretLevels = secretLevels;
    }

    private record AccessScope(LongPredicate categoryAllowed, List<Long> categoryIds, int maxSecretLevel) {}

    private AccessScope accessScope() {
        var subject = SecurityUtils.getSubject();
        boolean admin = ShiroUtils.isAdmin(getUserId());
        List<Long> categoryIds = new ArrayList<>();
        for (Choice choice : catalog.registrationChoices("",
            id -> admin || subject.isPermitted("archiveCategoryAll") || subject.isPermitted("archiveCategory_" + id)))
            categoryIds.add(choice.id());
        int maxSecretLevel = secretLevels.maxAllowed(admin, level -> subject.isPermitted("archiveSecret" + level));
        return new AccessScope(id -> admin || subject.isPermitted("archiveCategoryAll") || subject.isPermitted("archiveCategory_" + id),
            categoryIds, maxSecretLevel);
    }

    private ArchiveQuery scopedQuery(ArchiveQuery query) {
        AccessScope scope = accessScope();
        query.setOwnerName(getLoginName());
        query.setAdmin(ShiroUtils.isAdmin(getUserId()));
        query.setAllowedCategoryIds(scope.categoryIds());
        query.setMaxSecretLevel(scope.maxSecretLevel());
        return query;
    }

    public static class SubmitRequest {
        private List<Long> archiveIds;
        private Long locationId;
        private String storageMedia;
        private String electronicLocation;
        public List<Long> getArchiveIds() { return archiveIds; }
        public void setArchiveIds(List<Long> archiveIds) { this.archiveIds = archiveIds; }
        public Long getLocationId() { return locationId; }
        public void setLocationId(Long locationId) { this.locationId = locationId; }
        public String getStorageMedia() { return storageMedia; }
        public void setStorageMedia(String storageMedia) { this.storageMedia = storageMedia; }
        public String getElectronicLocation() { return electronicLocation; }
        public void setElectronicLocation(String electronicLocation) { this.electronicLocation = electronicLocation; }
    }

    public static class ReturnRequest {
        private long archiveId;
        private int version;
        private String reason;
        public long getArchiveId() { return archiveId; }
        public void setArchiveId(long archiveId) { this.archiveId = archiveId; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class CheckRequest {
        private List<Long> archiveIds;
        public List<Long> getArchiveIds() { return archiveIds; }
        public void setArchiveIds(List<Long> archiveIds) { this.archiveIds = archiveIds; }
    }

    public record CheckResult(long archiveId, String archiveCode, String title, String archiveStatus, boolean pass, List<CheckItem> items) {}

    @GetMapping("/archiving")
    @RequiresPermissions(QUERY)
    public String archivingPage(Model model) {
        var subject = SecurityUtils.getSubject();
        model.addAttribute("canSubmit", subject.isPermitted(SUBMIT));
        model.addAttribute("canReturn", subject.isPermitted(RETURN));
        return "archive/archiving";
    }

    @GetMapping("/batch")
    @RequiresPermissions(BATCH_QUERY)
    public String batchPage() {
        return "archive/batch";
    }

    @PostMapping("/archiving/list")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public TableDataInfo list(ArchiveQuery query) {
        // 状态查询：待归档（默认）与已归档共用一张表；已归档才有批次号、介质、电子存储位置、归档人。
        String status = query.getArchiveStatus();
        query.setArchiveStatus(normalizeStatus(status));
        scopedQuery(query);
        List<ArchiveRecord> rows = archiving.list(query);
        Map<Long, ArchiveFacts> facts = archiving.facts(rows.stream().map(ArchiveRecord::getArchiveId).toList());
        TableDataInfo table = getDataTable(rows);
        table.setRows(rows.stream().map(row -> archiveRowView(row, facts.get(row.getArchiveId()))).toList());
        return table;
    }

    /** 列表行：M01 字段 + 阶段2 归档属性（批次号、介质、电子存储位置、归档人），与小程序同名字段同义。 */
    private static Map<String, Object> archiveRowView(ArchiveRecord record, ArchiveFacts facts) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("archiveId", record.getArchiveId());
        view.put("archiveCode", nullSafe(record.getArchiveCode()));
        view.put("title", nullSafe(record.getTitle()));
        view.put("formingDate", record.getFormingDate() == null ? "" : record.getFormingDate().toString());
        view.put("categoryName", nullSafe(record.getCategoryName()));
        view.put("secretName", nullSafe(record.getSecretName()));
        view.put("retentionName", nullSafe(record.getRetentionName()));
        view.put("archiveStatus", nullSafe(record.getArchiveStatus()));
        view.put("version", record.getVersion() == null ? 0 : record.getVersion());
        view.put("createBy", nullSafe(record.getCreateBy()));
        // createTime 交给 Jackson 按 spring.jackson.date-format 输出，避免 Date.toString() 变成英文长格式。
        view.put("createTime", record.getCreateTime());
        view.put("batchNo", facts == null ? "" : facts.batchNo());
        view.put("storageMedia", facts == null ? "" : facts.storageMedia());
        view.put("mediumLabel", ArchiveArchivingService.mediumLabel(facts == null ? "" : facts.storageMedia()));
        view.put("electronicLocation", facts == null ? "" : facts.electronicLocation());
        view.put("archivedBy", facts == null ? "" : facts.archivedBy());
        view.put("archivedAt", facts == null ? "" : facts.archiveDate());
        view.put("locationCode", facts == null ? "" : facts.locationCode());
        view.put("locationPath", facts == null ? "" : facts.locationPath());
        return view;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 只允许「待归档」与「已归档」两种视图，其他取值一律按待归档处理，避免表单参数越界。 */
    private static String normalizeStatus(String status) {
        return ArchiveArchivingService.ARCHIVED.equals(status == null ? "" : status.trim().toUpperCase())
            ? ArchiveArchivingService.ARCHIVED : ArchiveArchivingService.SUBMITTED;
    }

    @PostMapping("/archiving/check")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult check(@RequestBody CheckRequest request) {
        AccessScope scope = accessScope();
        List<CheckResult> results = new ArrayList<>();
        for (Long archiveId : safeIds(request.getArchiveIds())) {
            ArchiveRecord record = archiving.requireArchive(archiveId, getLoginName(), getUserId(),
                ShiroUtils.isAdmin(getUserId()), scope.categoryAllowed(), scope.maxSecretLevel());
            List<CheckItem> items = archiving.check(record, scope.categoryAllowed());
            // 校验即留痕：逐项结果与当前版本一起落库，供事后追溯与复检判断。
            archiving.saveCheckReport(record, items, null, "CHECK", getLoginName(), getUserId());
            boolean pass = items.stream().allMatch(CheckItem::passed);
            results.add(new CheckResult(record.getArchiveId(), record.getArchiveCode(), record.getTitle(),
                record.getArchiveStatus(), pass, items));
        }
        return AjaxResult.success(results);
    }

    @PostMapping("/archiving/submit")
    @ResponseBody
    @RequiresPermissions(SUBMIT)
    @Log(title = "档案归档", businessType = BusinessType.UPDATE)
    public AjaxResult submit(@RequestBody SubmitRequest request) {
        AccessScope scope = accessScope();
        return AjaxResult.success(archiving.submit(request.getArchiveIds(), request.getLocationId(),
            new ArchiveArchivingService.Storage(request.getStorageMedia(), request.getElectronicLocation()),
            "MANUAL", "", getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/archiving/return")
    @ResponseBody
    @RequiresPermissions(RETURN)
    @Log(title = "档案归档", businessType = BusinessType.UPDATE)
    public AjaxResult returnToDraft(@RequestBody ReturnRequest request) {
        AccessScope scope = accessScope();
        archiving.returnToDraft(request.getArchiveId(), request.getVersion(), request.getReason(), getLoginName(),
            getUserId(), ShiroUtils.isAdmin(getUserId()), scope.categoryAllowed(), scope.maxSecretLevel());
        return success();
    }

    @GetMapping("/archiving/detail/{archiveId}")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult detail(@PathVariable long archiveId) {
        AccessScope scope = accessScope();
        ArchiveRecord record = archiving.requireArchive(archiveId, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId()), scope.categoryAllowed(), scope.maxSecretLevel());
        CheckReportRow latest = archiving.latestReport(archiveId);
        ArchiveFacts facts = archiving.facts(List.of(archiveId)).get(archiveId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archive", record);
        data.put("items", archiving.check(record, scope.categoryAllowed()));
        data.put("report", latest);
        data.put("reportStale", ArchiveArchivingService.reportStale(latest, record));
        data.put("facts", facts);
        data.put("mediumLabel", ArchiveArchivingService.mediumLabel(facts == null ? "" : facts.storageMedia()));
        return AjaxResult.success(data);
    }

    /** 每条档案最新一次校验结论，供待归档列表标记「待补正」。 */
    @PostMapping("/archiving/checkstates")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult checkStates(@RequestBody CheckRequest request) {
        AccessScope scope = accessScope();
        boolean admin = ShiroUtils.isAdmin(getUserId());
        List<Long> allowed = new ArrayList<>();
        for (Long archiveId : safeIds(request.getArchiveIds())) {
            try {
                archiving.requireArchive(archiveId, getLoginName(), getUserId(), admin,
                    scope.categoryAllowed(), scope.maxSecretLevel());
                allowed.add(archiveId);
            } catch (ServiceException e) {
                // 越权或档案不存在：不返回它的校验状态
            }
        }
        return AjaxResult.success(archiving.latestCheckStates(allowed));
    }

    /** 校验报告：最新一次 + 历史，并标出报告是否已过期（档案换版本后需复检）。 */
    @GetMapping("/archiving/report/{archiveId}")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult report(@PathVariable long archiveId) {
        AccessScope scope = accessScope();
        ArchiveRecord record = archiving.requireArchive(archiveId, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId()), scope.categoryAllowed(), scope.maxSecretLevel());
        CheckReportRow latest = archiving.latestReport(archiveId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("latest", latest);
        data.put("history", archiving.reports(archiveId, 20));
        data.put("stale", ArchiveArchivingService.reportStale(latest, record));
        data.put("archiveVersion", record.getVersion());
        // 未建报告表时提示「未启用」，避免看起来像功能缺失。
        data.put("reportEnabled", archiving.reportEnabled());
        return AjaxResult.success(data);
    }

    @PostMapping("/archiving/statistics")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult statistics(ArchiveQuery query) {
        scopedQuery(query);
        return AjaxResult.success(archiving.statistics(query));
    }

    @GetMapping("/archiving/export")
    @RequiresPermissions(QUERY)
    @Log(title = "档案归档", businessType = BusinessType.EXPORT)
    public void export(ArchiveQuery query, HttpServletResponse response) throws IOException {
        query.setArchiveStatus(normalizeStatus(query.getArchiveStatus()));
        scopedQuery(query);
        query.setMaxRows(10000);
        List<ArchiveRecord> rows = archiving.list(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=archiving.xlsx");
        ArchiveM10Workbook.writePending(response.getOutputStream(), rows);
    }

    @PostMapping("/batch/list")
    @ResponseBody
    @RequiresPermissions(BATCH_QUERY)
    public Map<String, Object> batchList(String keyword, String status, String from, String to,
                                         Integer pageNum, Integer pageSize) {
        Map<String, Object> data = archiving.listBatches(keyword, status, from, to,
            pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        @SuppressWarnings("unchecked")
        List<BatchRow> rows = (List<BatchRow>) data.get("rows");
        Map<Long, BatchFacts> facts = archiving.batchFacts(rows.stream().map(BatchRow::batchId).toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "查询成功");
        result.put("rows", rows.stream().map(row -> batchRowView(row, facts.get(row.batchId()))).toList());
        result.put("total", data.get("total"));
        return result;
    }

    /** 批次行：原字段 + 阶段2 来源（source/sourceLabel/sourceRef）与库位编码，与小程序同名字段同义。 */
    private static Map<String, Object> batchRowView(BatchRow row, BatchFacts facts) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("batchId", row.batchId());
        view.put("batchNo", nullSafe(row.batchNo()));
        view.put("applicantName", nullSafe(row.applicantName()));
        view.put("totalCount", row.totalCount());
        view.put("successCount", row.successCount());
        view.put("failCount", row.failCount());
        view.put("batchStatus", nullSafe(row.batchStatus()));
        view.put("statusLabel", ArchiveArchivingService.batchStatusLabel(row.batchStatus()));
        view.put("submitTime", nullSafe(row.submitTime()));
        view.put("finishTime", nullSafe(row.finishTime()));
        view.put("locationId", row.locationId() == null ? "" : row.locationId().toString());
        view.put("locationCode", facts == null ? "" : facts.locationCode());
        view.put("remark", nullSafe(row.remark()));
        String sourceType = facts == null ? "" : facts.sourceType();
        view.put("source", sourceType);
        view.put("sourceLabel", ArchiveArchivingService.sourceLabel(sourceType));
        view.put("sourceRef", facts == null ? "" : facts.sourceRef());
        return view;
    }

    private static Map<String, Object> batchItemView(BatchItemRow item, ArchiveFacts facts) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("itemId", item.itemId());
        view.put("batchId", item.batchId());
        view.put("archiveId", item.archiveId());
        view.put("archiveCode", nullSafe(item.archiveCode()));
        view.put("title", nullSafe(item.title()));
        view.put("itemResult", nullSafe(item.itemResult()));
        view.put("reasonCode", nullSafe(item.reasonCode()));
        view.put("reason", nullSafe(item.reason()));
        view.put("mediumLabel", ArchiveArchivingService.mediumLabel(facts == null ? "" : facts.storageMedia()));
        view.put("electronicLocation", facts == null ? "" : facts.electronicLocation());
        view.put("archivedBy", facts == null ? "" : facts.archivedBy());
        view.put("locationCode", facts == null ? "" : facts.locationCode());
        return view;
    }

    @GetMapping("/batch/detail/{batchId}")
    @ResponseBody
    @RequiresPermissions(BATCH_QUERY)
    public AjaxResult batchDetail(@PathVariable long batchId) {
        BatchRow batch = archiving.batchDetail(batchId);
        List<BatchItemRow> items = archiving.batchItems(batchId);
        // 与小程序 batch/detail 共用 batchItemFacts()，保证两端介质/归档人口径一致。
        Map<Long, ArchiveFacts> facts = archiving.batchItemFacts(batchId);
        Map<Long, BatchFacts> batchFacts = archiving.batchFacts(List.of(batchId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batch", batchRowView(batch, batchFacts.get(batchId)));
        data.put("items", items.stream().map(item -> batchItemView(item, facts.get(item.archiveId()))).toList());
        return AjaxResult.success(data);
    }

    @GetMapping("/batch/export/{batchId}")
    @RequiresPermissions(BATCH_QUERY)
    @Log(title = "档案归档批次", businessType = BusinessType.EXPORT)
    public void batchExport(@PathVariable long batchId, HttpServletResponse response) throws IOException {
        BatchRow batch = archiving.batchDetail(batchId);
        List<BatchItemRow> items = archiving.batchItems(batchId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=batch-" + batchId + ".xlsx");
        ArchiveM10Workbook.writeBatchItems(response.getOutputStream(), batch, items);
    }

    private static List<Long> safeIds(List<Long> ids) {
        return ids == null ? new ArrayList<>() : ids;
    }

    /** 归档属性校验失败（介质为电子却没填电子存储位置、库位无效）：PC 只要中文提示。 */
    @ExceptionHandler(ArchiveArchivingService.ArchivingException.class)
    @org.springframework.web.bind.annotation.ResponseBody
    public AjaxResult archivingError(ArchiveArchivingService.ArchivingException error) {
        return AjaxResult.error(error.getMessage());
    }
}
