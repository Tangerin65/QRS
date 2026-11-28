package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveArchivingService;
import com.ruoyi.system.service.ArchiveArchivingService.ArchiveFacts;
import com.ruoyi.system.service.ArchiveArchivingService.ArchivingException;
import com.ruoyi.system.service.ArchiveArchivingService.CheckReportRow;
import com.ruoyi.system.service.ArchiveArchivingService.RetryResult;
import com.ruoyi.system.service.ArchiveArchivingService.Storage;
import com.ruoyi.system.service.ArchiveArchivingService.SubmitItem;
import com.ruoyi.system.service.ArchiveArchivingService.SubmitResult;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveLocationService;
import com.ruoyi.system.service.ArchiveLocationService.LocationRow;
import com.ruoyi.system.service.ArchiveLocationService.LocationStorage;
import com.ruoyi.system.service.ArchiveSecretLevels;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.support.ArchiveCryptoUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * M10 归档 · 小程序接口（契约见 doc/m10-mobile-api.md，9 个接口）。
 *
 * 鉴权与成员3 的 /api/mobile/archives 同一套：控制器 @Anonymous + 手工校验 Bearer 令牌
 * （wx_token + wx_binding），失败 401。业务失败统一 HTTP 400 + 字符串错误码，
 * 让小程序 utils/request.js 能直接展示后端 msg；401/403/500 用数字码，不占业务码空间。
 */
@RestController
@RequestMapping("/api/mobile/archiving")
public class MobileArchivingController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(MobileArchivingController.class);
    private static final String QUERY = "archiveArchivingQuery";
    private static final String SUBMIT = "archiveArchivingSubmit";
    private static final String BATCH = "archiveBatchQuery";
    private static final String LOCATION = "archiveLocationQuery";
    private static final String ARCHIVE_NOT_FOUND = "档案不存在或无权限访问";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final ISysUserService users;
    private final ISysMenuService menus;
    private final ArchiveCatalogService catalog;
    private final ArchiveSecretLevels secretLevels;
    private final ArchiveArchivingService archiving;
    private final ArchiveLocationService locations;

    public MobileArchivingController(JdbcTemplate jdbc, ISysUserService users, ISysMenuService menus,
            ArchiveCatalogService catalog, ArchiveSecretLevels secretLevels, ArchiveArchivingService archiving,
            ArchiveLocationService locations) {
        this.jdbc = jdbc;
        this.users = users;
        this.menus = menus;
        this.catalog = catalog;
        this.secretLevels = secretLevels;
        this.archiving = archiving;
        this.locations = locations;
    }

    // ---------------------------------------------------------------- 入参

    public record PendingQuery(Integer pageNum, Integer pageSize, String filter, String keyword, Long categoryId) {}

    public record CheckQuery(Long archiveId, String archiveCode) {}

    public record JoinBatchQuery(Long archiveId, String archiveCode, Long locationId, String storageMedia,
                                 String electronicLocation) {}

    public record StatusQuery(Integer pageNum, Integer pageSize, String keyword, String filter, String archiveStatus) {}

    public record BatchQuery(Integer pageNum, Integer pageSize, String filter, String keyword, String from, String to) {}

    public record BatchNoQuery(String batchNo) {}

    public record LocationQuery(Integer pageNum, Integer pageSize, String filter, String keyword, String level) {}

    // ---------------------------------------------------------------- 1. hub 汇总

    @Anonymous
    @GetMapping("/hub/summary")
    public ResponseEntity<AjaxResult> hubSummary(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header) {
        Scope scope = scope(header, QUERY);
        return ok(archiving.hubSummary(scopedQuery(scope)));
    }

    // ---------------------------------------------------------------- 2. 待归档列表

    @Anonymous
    @PostMapping("/pending/list")
    public ResponseEntity<AjaxResult> pendingList(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) PendingQuery body) {
        Scope scope = scope(header, QUERY);
        PendingQuery input = body == null ? new PendingQuery(null, null, null, null, null) : body;
        String filter = oneOf(input.filter(), "ALL", "ALL", "NEED_FIX", "UNKNOWN");
        String keyword = keyword(input.keyword());
        int page = pageNum(input.pageNum());
        int size = pageSize(input.pageSize());
        Map<String, String> dict = archiving.retentionDict();
        if ("ALL".equals(filter)) {
            ArchiveQuery query = scopedQuery(scope);
            query.setArchiveStatus(ArchiveArchivingService.SUBMITTED);
            query.setKeyword(keyword);
            query.setCategoryId(input.categoryId());
            query.setPageNum(page);
            query.setPageSize(size);
            List<ArchiveRecord> rows = archiving.list(query);
            long total = getDataTable(rows).getTotal();
            return ok(paged(rows, total, page, size, dict));
        }
        // NEED_FIX / UNKNOWN 要按字典标签判定期限，SQL 无法等价表达，先按上限扫描再内存分页。
        ArchiveQuery scan = scopedQuery(scope);
        scan.setArchiveStatus(ArchiveArchivingService.SUBMITTED);
        scan.setKeyword(keyword);
        scan.setCategoryId(input.categoryId());
        scan.setMaxRows(ArchiveArchivingService.HUB_SCAN);
        scan.setPageNum(1);
        scan.setPageSize(ArchiveArchivingService.HUB_SCAN);
        List<ArchiveRecord> scanned = archiving.list(scan);
        Map<Long, CheckReportRow> reports = archiving.latestReports(ids(scanned));
        List<ArchiveRecord> filtered = scanned.stream().filter(record -> {
            boolean unknown = "UNKNOWN".equals(archiving.retentionView(record, dict).get("state"));
            if ("UNKNOWN".equals(filter)) return unknown;
            CheckReportRow report = reports.get(record.getArchiveId());
            boolean failed = report != null && "FAIL".equals(report.reportResult())
                && !ArchiveArchivingService.reportStale(report, record);
            return unknown || failed;
        }).toList();
        return ok(paged(page(filtered, page, size), filtered.size(), page, size, dict));
    }

    // ---------------------------------------------------------------- 3. 检查（两个页面共用）

    @Anonymous
    @PostMapping("/check")
    public ResponseEntity<AjaxResult> check(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) CheckQuery body) {
        Scope scope = scope(header, QUERY);
        CheckQuery input = body == null ? new CheckQuery(null, null) : body;
        ArchiveRecord record = requireArchive(input.archiveId(), input.archiveCode(), scope);
        List<ArchiveArchivingService.CheckItem> items = archiving.check(record, scope.categories());
        archiving.saveCheckReport(record, items, null, "CHECK", scope.actor(), scope.user().getUserId());
        CheckReportRow saved = archiving.latestReport(record.getArchiveId());
        boolean pass = items.stream().allMatch(ArchiveArchivingService.CheckItem::passed);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archiveId", Long.toString(record.getArchiveId()));
        data.put("archiveCode", nullSafe(record.getArchiveCode()));
        data.put("pass", pass);
        data.put("retention", archiving.retentionView(record, archiving.retentionDict()));
        data.put("reportId", saved == null ? "" : Long.toString(saved.reportId()));
        data.put("reportEnabled", archiving.reportEnabled());
        data.put("checkedAt", LocalDateTime.now().format(TIME));
        data.put("checks", items);
        data.put("message", pass ? "重新检查完成：全部通过" : "重新检查完成：仍有未通过项");
        return ok(data);
    }

    // ---------------------------------------------------------------- 4. 加入批次（单件组批并立即归档）

    @Anonymous
    @PostMapping("/pending/join-batch")
    public ResponseEntity<AjaxResult> joinBatch(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) JoinBatchQuery body) {
        Scope scope = scope(header, SUBMIT);
        JoinBatchQuery input = body == null ? new JoinBatchQuery(null, null, null, null, null) : body;
        ArchiveRecord record = requireArchive(input.archiveId(), input.archiveCode(), scope);
        if (ArchiveArchivingService.ARCHIVED.equals(record.getArchiveStatus())) {
            // 幂等：已归档直接回原批次号，前端提示「已归档」。
            String batchNo = archivedBatchNo(record.getArchiveId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("batchNo", batchNo);
            data.put("archiveId", Long.toString(record.getArchiveId()));
            data.put("archiveCode", nullSafe(record.getArchiveCode()));
            data.put("archiveStatus", ArchiveArchivingService.ARCHIVED);
            data.put("locationCode", locationCodeOf(record));
            data.put("archivedAt", archivedAtOf(record));
            data.put("message", batchNo.isEmpty() ? "已归档" : "已归档，批次号 " + batchNo);
            return ok(data);
        }
        if (!ArchiveArchivingService.SUBMITTED.equals(record.getArchiveStatus())) {
            throw new ArchivingException("ARCHIVE_STATUS_CONFLICT",
                "档案当前状态为" + statusLabel(record.getArchiveStatus()) + "，只有「待归档」档案可以归档");
        }
        Storage storage = new Storage(input.storageMedia(), input.electronicLocation());
        SubmitResult result = archiving.submit(List.of(record.getArchiveId()), input.locationId(), storage, "MOBILE", "",
            scope.actor(), scope.user().getUserId(), scope.user().isAdmin(), scope.categories(), scope.maxSecret());
        SubmitItem item = result.items().isEmpty()
            ? new SubmitItem(record.getArchiveId(), record.getArchiveCode(), record.getTitle(), "FAIL", "DENIED",
                "归档未完成", List.of())
            : result.items().get(0);
        if (!"OK".equals(item.result())) throw failure(item);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchNo", result.batchNo());
        data.put("archiveId", Long.toString(record.getArchiveId()));
        data.put("archiveCode", nullSafe(item.archiveCode()));
        data.put("archiveStatus", ArchiveArchivingService.ARCHIVED);
        data.put("locationCode", input.locationId() == null ? "" : safeLocationCode(input.locationId()));
        data.put("archivedAt", LocalDateTime.now().format(TIME));
        data.put("message", "已归档，批次号 " + result.batchNo());
        return ok(data);
    }

    // ---------------------------------------------------------------- 5. 归档状态查询

    @Anonymous
    @PostMapping("/status/search")
    public ResponseEntity<AjaxResult> statusSearch(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) StatusQuery body) {
        Scope scope = scope(header, QUERY);
        StatusQuery input = body == null ? new StatusQuery(null, null, null, null, null) : body;
        String filter = oneOf(input.filter(), "ALL", "ALL", "PERMANENT", "FIXED", "UNKNOWN");
        String status = oneOf(input.archiveStatus(), "ALL", "ALL", "DRAFT", "SUBMITTED", "ARCHIVED");
        String keyword = keyword(input.keyword());
        int page = pageNum(input.pageNum());
        int size = pageSize(input.pageSize());
        Map<String, String> dict = archiving.retentionDict();
        ArchiveQuery query = scopedQuery(scope);
        query.setKeyword(keyword);
        if (!"ALL".equals(status)) query.setArchiveStatus(status);
        if ("ALL".equals(filter)) {
            query.setPageNum(page);
            query.setPageSize(size);
            List<ArchiveRecord> rows = archiving.anyStatus(query);
            return ok(paged(rows, getDataTable(rows).getTotal(), page, size, dict));
        }
        // 期限筛选要按字典标签判定，SQL 无法等价表达：上限扫描后再内存分页。
        query.setMaxRows(ArchiveArchivingService.HUB_SCAN);
        query.setPageNum(1);
        query.setPageSize(ArchiveArchivingService.HUB_SCAN);
        List<ArchiveRecord> scanned = archiving.anyStatus(query);
        List<ArchiveRecord> filtered = scanned.stream()
            .filter(record -> "ALL".equals(filter)
                || filter.equals(archiving.retentionView(record, dict).get("state")))
            .toList();
        return ok(paged(page(filtered, page, size), filtered.size(), page, size, dict));
    }

    // ---------------------------------------------------------------- 6. 批次列表

    @Anonymous
    @PostMapping("/batch/list")
    public ResponseEntity<AjaxResult> batchList(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) BatchQuery body) {
        scope(header, BATCH);
        BatchQuery input = body == null ? new BatchQuery(null, null, null, null, null, null) : body;
        String filter = oneOf(input.filter(), "ALL", "ALL", "OPEN", "RETURNED");
        String keyword = keyword(input.keyword());
        String from = date(input.from());
        String to = date(input.to());
        return ok(archiving.mobileBatches(keyword, filter, from, to, pageNum(input.pageNum()),
            pageSize(input.pageSize())));
    }

    // ---------------------------------------------------------------- 7. 批次详情

    @Anonymous
    @GetMapping("/batch/detail")
    public ResponseEntity<AjaxResult> batchDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestParam(value = "batchNo", required = false) String batchNo) {
        scope(header, BATCH);
        if (batchNo == null || batchNo.isBlank()) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：batchNo", Map.of("field", "batchNo"));
        }
        return ok(archiving.mobileBatchDetail(batchNo.trim()));
    }

    // ---------------------------------------------------------------- 8. 重新组批

    @Anonymous
    @PostMapping("/batch/retry")
    public ResponseEntity<AjaxResult> batchRetry(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) BatchNoQuery body) {
        Scope scope = scope(header, SUBMIT);
        String batchNo = body == null || body.batchNo() == null ? "" : body.batchNo().trim();
        if (batchNo.isEmpty()) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：batchNo", Map.of("field", "batchNo"));
        }
        RetryResult retry = archiving.retryBatch(batchNo, scope.actor(), scope.user().getUserId(),
            scope.user().isAdmin(), scope.categories(), scope.maxSecret());
        SubmitResult batch = retry.batch();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceBatchNo", retry.sourceBatchNo());
        data.put("batchNo", batch.batchNo());
        data.put("total", batch.total());
        data.put("success", batch.success());
        data.put("fail", batch.fail());
        List<Map<String, Object>> items = new ArrayList<>();
        for (SubmitItem item : batch.items()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("archiveId", Long.toString(item.archiveId()));
            view.put("archiveCode", nullSafe(item.archiveCode()));
            view.put("title", nullSafe(item.title()));
            view.put("itemResult", nullSafe(item.result()));
            view.put("reasonCode", nullSafe(item.reasonCode()));
            view.put("reason", nullSafe(item.reason()));
            items.add(view);
        }
        data.put("items", items);
        data.put("message", "已重新组批：" + batch.batchNo());
        return ok(data);
    }

    // ---------------------------------------------------------------- 9. 库位列表

    @Anonymous
    @PostMapping("/location/list")
    public ResponseEntity<AjaxResult> locationList(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody(required = false) LocationQuery body) {
        scope(header, LOCATION);
        LocationQuery input = body == null ? new LocationQuery(null, null, null, null, null) : body;
        String filter = oneOf(input.filter(), "ALL", "ALL", "PAPER", "ELECTRONIC", "MIXED");
        String keyword = keyword(input.keyword());
        String level = oneOf(input.level(), "RACK", "WAREHOUSE", "AREA", "RACK");
        int page = pageNum(input.pageNum());
        int size = pageSize(input.pageSize());
        Map<Long, LocationStorage> storage = locations.storageByLocation();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocationRow row : locations.tree(keyword, level, null)) {
            LocationStorage item = storage.get(row.locationId());
            String medium = item == null ? "" : item.medium();
            if (!"ALL".equals(filter) && !filter.equals(medium)) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("locationId", Long.toString(row.locationId()));
            view.put("locationCode", nullSafe(row.locationCode()));
            view.put("locationName", nullSafe(row.locationName()));
            view.put("level", nullSafe(row.level()));
            view.put("levelName", nullSafe(row.levelName()));
            view.put("levelPath", nullSafe(row.levelPath()));
            view.put("house", houseOf(row.levelPath()));
            view.put("capacity", row.capacity());
            view.put("used", row.usedCount());
            view.put("unit", "件");
            view.put("percent", percent(row.usedCount(), row.capacity()));
            view.put("status", row.status());
            view.put("statusLabel", row.status() == 0 ? "启用" : "停用");
            view.put("medium", medium);
            view.put("mediumLabel", ArchiveLocationService.mediumLabel(medium));
            view.put("electronicPath", item == null ? "" : item.electronicPath());
            view.put("remark", nullSafe(row.remark()));
            view.put("archives", List.of());
            rows.add(view);
        }
        List<Map<String, Object>> pageRows = page(rows, page, size);
        Map<Long, List<Map<String, String>>> shelved = shelvedArchives(idsOf(pageRows));
        pageRows.forEach(view -> view.put("archives",
            shelved.getOrDefault(Long.parseLong((String) view.get("locationId")), List.of())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", pageRows);
        data.put("total", rows.size());
        data.put("pageNum", page);
        data.put("pageSize", size);
        data.put("hasMore", (long) page * size < rows.size());
        return ok(data);
    }

    // ---------------------------------------------------------------- 权限与数据范围

    private record Scope(SysUser user, Predicate<String> allowed, LongPredicate categories, int maxSecret) {
        String actor() {
            return user.getLoginName();
        }

    }

    /** 数据范围沿用 M01：本人登记（管理员不限）+ 分类授权 + 密级上限。 */
    private ArchiveQuery scopedQuery(Scope scope) {
        ArchiveQuery query = new ArchiveQuery();
        query.setOwnerName(scope.actor());
        query.setAdmin(scope.user().isAdmin());
        query.setAllowedCategoryIds(catalog.registrationChoices("", scope.categories()).stream()
            .map(ArchiveCatalogService.Choice::id).toList());
        query.setMaxSecretLevel(scope.maxSecret());
        return query;
    }

    private Scope scope(String header, String... required) {
        SysUser user = findMiniappUser(header);
        if (user == null || !"0".equals(user.getDelFlag())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        }
        if (!"0".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
        }
        var permissions = menus.selectPermsByUserId(user.getUserId());
        Predicate<String> allowed = key -> user.isAdmin() || permissions.contains(key);
        for (String key : required) {
            if (!allowed.test(key)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有执行此操作的权限");
            }
        }
        LongPredicate categories = id -> allowed.test("archiveCategoryAll") || allowed.test("archiveCategory_" + id);
        int maxSecret = secretLevels.maxAllowed(user.isAdmin(), level -> allowed.test("archiveSecret" + level));
        return new Scope(user, allowed, categories, maxSecret);
    }

    private SysUser findMiniappUser(String header) {
        if (header == null || header.length() > 263 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            return null;
        }
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema=database() and table_name in ('wx_token','wx_binding')
                """, Integer.class);
        if (tables == null || tables != 2) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "小程序账号服务尚未接入");
        }
        String digest = ArchiveCryptoUtils.sha256Hex(header.substring(7).strip());
        var ids = jdbc.queryForList("""
                select t.user_id from wx_token t
                where t.token_digest=? and t.client_type='miniapp' and t.revoked_at is null and t.expires_at>now()
                  and exists (select 1 from wx_binding b where b.user_id=t.user_id
                              and b.bind_version=t.bind_version and b.status=1)
                """, Long.class, digest);
        return ids.isEmpty() ? null : users.selectUserById(ids.get(0));
    }

    // ---------------------------------------------------------------- 档案与批次取值

    private ArchiveRecord requireArchive(Long archiveId, String archiveCode, Scope scope) {
        long id;
        if (archiveId != null && archiveId > 0) {
            id = archiveId;
        } else {
            String code = archiveCode == null ? "" : archiveCode.trim();
            if (code.isEmpty()) {
                throw new ArchivingException("PARAM_INVALID", "参数不合法：archiveCode", Map.of("field", "archiveCode"));
            }
            List<Long> ids = jdbc.queryForList("select archive_id from arc_archive where archive_code=? and del_flag='0'",
                Long.class, code);
            if (ids.isEmpty()) throw new ArchivingException("ARCHIVE_NOT_FOUND", ARCHIVE_NOT_FOUND);
            id = ids.get(0);
        }
        try {
            return archiving.requireArchive(id, scope.actor(), scope.user().getUserId(), scope.user().isAdmin(),
                scope.categories(), scope.maxSecret());
        } catch (ServiceException e) {
            // 越权、已删除、不存在合并为一个错误码，避免被用来探测档号。
            throw new ArchivingException("ARCHIVE_NOT_FOUND", ARCHIVE_NOT_FOUND);
        }
    }

    /** 已归档档案的批次号：优先取 archive_batch_id，列未迁移时退回批次明细。 */
    private String archivedBatchNo(long archiveId) {
        ArchiveFacts facts = archiving.facts(List.of(archiveId)).get(archiveId);
        if (facts != null && !facts.batchNo().isEmpty()) return facts.batchNo();
        List<String> batchNos = jdbc.queryForList("select b.batch_no from arc_archive_batch_item i "
            + "join arc_archive_batch b on b.batch_id=i.batch_id "
            + "where i.archive_id=? and i.item_result='OK' order by i.item_id desc limit 1", String.class, archiveId);
        return batchNos.isEmpty() ? "" : nullSafe(batchNos.get(0));
    }

    private String locationCodeOf(ArchiveRecord record) {
        ArchiveFacts facts = archiving.facts(List.of(record.getArchiveId())).get(record.getArchiveId());
        return facts == null ? "" : facts.locationCode();
    }

    private String archivedAtOf(ArchiveRecord record) {
        ArchiveFacts facts = archiving.facts(List.of(record.getArchiveId())).get(record.getArchiveId());
        return facts == null ? "" : facts.archiveDate();
    }

    private String safeLocationCode(Long locationId) {
        try {
            return locations.require(locationId).locationCode();
        } catch (ServiceException e) {
            return "";
        }
    }

    private Map<Long, List<Map<String, String>>> shelvedArchives(List<Long> locationIds) {
        if (locationIds.isEmpty()) return Collections.emptyMap();
        String placeholders = locationIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        Map<Long, List<Map<String, String>>> shelved = new LinkedHashMap<>();
        jdbc.query("select location_id,archive_code,title from arc_archive where del_flag='0' "
                + "and archive_status='ARCHIVED' and location_id in (" + placeholders + ") order by archive_code",
            rs -> {
                long locationId = rs.getLong("location_id");
                List<Map<String, String>> list = shelved.computeIfAbsent(locationId, key -> new ArrayList<>());
                if (list.size() >= 20) return;
                Map<String, String> item = new LinkedHashMap<>();
                item.put("archiveCode", nullSafe(rs.getString("archive_code")));
                item.put("title", nullSafe(rs.getString("title")));
                list.add(item);
            }, locationIds.toArray());
        return shelved;
    }

    private ArchivingException failure(SubmitItem item) {
        List<ArchiveArchivingService.CheckItem> failed = item.failedChecks();
        if ("RETENTION".equals(item.reasonCode()) || (failed.size() == 1 && "RETENTION".equals(failed.get(0).code()))) {
            String detail = failed.stream().filter(check -> "RETENTION".equals(check.code()))
                .map(ArchiveArchivingService.CheckItem::detail).findFirst().orElse(item.reason());
            return new ArchivingException("RETENTION_UNRECOGNIZED", "保管期限无法识别：" + detail + "，请先补正期限");
        }
        if ("CONFLICT".equals(item.reasonCode())) {
            return new ArchivingException("VERSION_CONFLICT", "档案已被修改，请刷新后重试");
        }
        if ("DENIED".equals(item.reasonCode())) {
            return new ArchivingException("ARCHIVE_NOT_FOUND", ARCHIVE_NOT_FOUND);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ArchiveArchivingService.CheckItem check : failed) {
            if (check.passed()) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", check.code());
            view.put("label", check.label());
            view.put("detail", check.detail());
            items.add(view);
        }
        ArchiveArchivingService.CheckItem first = failed.stream().filter(check -> !check.passed()).findFirst()
            .orElse(null);
        String message = first == null ? "归档未完成，请补正后重试"
            : "仍有检查项未通过：" + first.label() + "（" + first.detail() + "）；补正后才能归档";
        return new ArchivingException("CHECK_NOT_PASSED", message, Map.of("items", items));
    }

    // ---------------------------------------------------------------- 列表行

    private Map<String, Object> paged(List<ArchiveRecord> rows, long total, int page, int size,
                                      Map<String, String> dict) {
        Map<Long, ArchiveFacts> facts = archiving.facts(ids(rows));
        Map<Long, CheckReportRow> reports = archiving.latestReports(ids(rows));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows.stream()
            .map(row -> archiveView(row, facts.get(row.getArchiveId()), reports.get(row.getArchiveId()), dict))
            .toList());
        data.put("total", total);
        data.put("pageNum", page);
        data.put("pageSize", size);
        data.put("hasMore", (long) page * size < total);
        return data;
    }

    private Map<String, Object> archiveView(ArchiveRecord record, ArchiveFacts facts, CheckReportRow report,
                                            Map<String, String> dict) {
        String medium = facts == null ? "" : facts.storageMedia();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("archiveId", Long.toString(record.getArchiveId()));
        view.put("archiveCode", nullSafe(record.getArchiveCode()));
        view.put("title", nullSafe(record.getTitle()));
        view.put("categoryName", nullSafe(record.getCategoryName()));
        view.put("formingDate", record.getFormingDate() == null ? "" : record.getFormingDate().toString());
        view.put("version", record.getVersion() == null ? 0 : record.getVersion());
        view.put("retention", archiving.retentionView(record, dict));
        view.put("medium", medium);
        view.put("mediumLabel", ArchiveArchivingService.mediumLabel(medium));
        view.put("electronicLocation", facts == null ? "" : facts.electronicLocation());
        view.put("storageText", storageText(facts));
        view.put("batchNo", facts == null ? "" : facts.batchNo());
        view.put("locationCode", facts == null ? "" : facts.locationCode());
        view.put("locationPath", facts == null ? "" : facts.locationPath());
        view.put("archiveStatus", nullSafe(record.getArchiveStatus()));
        view.put("statusLabel", statusLabel(record.getArchiveStatus()));
        view.put("archivedAt", facts == null ? "" : facts.archiveDate());
        view.put("archivedBy", facts == null ? "" : facts.archivedBy());
        view.put("lastCheckAt", report == null ? "" : report.checkTime());
        view.put("checkedAt", report == null ? "" : report.checkTime());
        view.put("check", checkSummary(report, record));
        view.put("checks", report == null ? List.of() : report.items());
        return view;
    }

    private static Map<String, Object> checkSummary(CheckReportRow report, ArchiveRecord record) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (report == null) {
            summary.put("passed", 0);
            summary.put("total", 0);
            summary.put("tone", "warn");
            summary.put("label", "未校验");
            summary.put("checkedAt", "");
            return summary;
        }
        int passed = Math.max(0, report.itemTotal() - report.failCount());
        boolean stale = ArchiveArchivingService.reportStale(report, record);
        boolean pass = "PASS".equals(report.reportResult()) && !stale;
        summary.put("passed", passed);
        summary.put("total", report.itemTotal());
        summary.put("tone", stale ? "warn" : (pass ? "pass" : "fail"));
        summary.put("label", stale ? "需复检" : (pass ? "检查通过" : "需补正"));
        summary.put("checkedAt", nullSafe(report.checkTime()));
        return summary;
    }

    private static String storageText(ArchiveFacts facts) {
        if (facts == null) return "";
        String path = nullSafe(facts.locationPath());
        String electronic = nullSafe(facts.electronicLocation());
        if (path.isEmpty()) return electronic;
        return electronic.isEmpty() ? path : path + " · " + electronic;
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "待归档";
            case "ARCHIVED" -> "已归档";
            default -> nullSafe(status);
        };
    }

    private static String houseOf(String levelPath) {
        if (levelPath == null || levelPath.isBlank()) return "";
        int index = levelPath.indexOf(" / ");
        return index < 0 ? levelPath : levelPath.substring(0, index);
    }

    private static int percent(long used, Integer capacity) {
        if (capacity == null || capacity <= 0) return 0;
        return (int) Math.min(100, Math.round(used * 100.0 / capacity));
    }

    private static List<Long> ids(List<ArchiveRecord> rows) {
        return rows.stream().map(ArchiveRecord::getArchiveId).toList();
    }

    private static List<Long> idsOf(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> Long.parseLong((String) row.get("locationId"))).toList();
    }

    private static <T> List<T> page(List<T> rows, int page, int size) {
        int from = (page - 1) * size;
        if (from >= rows.size()) return List.of();
        return rows.subList(from, Math.min(rows.size(), from + size));
    }

    // ---------------------------------------------------------------- 参数

    private static int pageNum(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private static int pageSize(Integer value) {
        return value == null ? 20 : Math.min(MAX_PAGE_SIZE, Math.max(1, value));
    }

    private static String keyword(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 200) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：keyword", Map.of("field", "keyword"));
        }
        return text;
    }

    private static String date(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ArchivingException("PARAM_INVALID", "参数不合法：日期格式应为 yyyy-MM-dd",
                Map.of("field", "date"));
        }
        return text;
    }

    private static String oneOf(String value, String fallback, String... allowed) {
        String text = value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
        for (String option : allowed) {
            if (option.equals(text)) return text;
        }
        throw new ArchivingException("PARAM_INVALID", "参数不合法：" + value, Map.of("field", "filter"));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    // ---------------------------------------------------------------- 响应

    private ResponseEntity<AjaxResult> ok(Object data) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(AjaxResult.success(data));
    }

    private ResponseEntity<AjaxResult> failed(int status, Object code, String message, Object data) {
        AjaxResult body = new AjaxResult();
        body.put("code", code == null ? status : code);
        body.put("msg", message);
        if (data != null) body.put("data", data);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AjaxResult> accessError(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        return failed(status, status, error.getReason() == null ? "操作未完成" : error.getReason(), null);
    }

    @ExceptionHandler(ArchivingException.class)
    public ResponseEntity<AjaxResult> businessError(ArchivingException error) {
        return failed(HttpStatus.BAD_REQUEST.value(), error.code(), error.getMessage(),
            error.data().isEmpty() ? null : error.data());
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> serviceError(ServiceException error) {
        return failed(HttpStatus.BAD_REQUEST.value(), "PARAM_INVALID", error.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AjaxResult> unexpectedError(Exception error) {
        if (error instanceof HttpMessageNotReadableException) {
            return failed(HttpStatus.BAD_REQUEST.value(), "PARAM_INVALID", "请求参数格式不正确", null);
        }
        if (error instanceof ErrorResponse requestError && requestError.getStatusCode().is4xxClientError()) {
            return failed(requestError.getStatusCode().value(), requestError.getStatusCode().value(),
                "请求参数不正确", null);
        }
        log.error("小程序归档接口处理失败", error);
        return failed(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "归档操作暂时失败", null);
    }
}
