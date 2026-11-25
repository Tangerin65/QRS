package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveFile;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveAttachmentIndexer;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveFileService;
import com.ruoyi.system.service.ArchiveSecretLevels;
import com.ruoyi.system.service.IArchiveService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.support.ArchiveCryptoUtils;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mobile/archives")
public class MobileArchiveSearchController extends BaseController {
    private final JdbcTemplate jdbc;
    private final ISysUserService users;
    private final ISysMenuService menus;
    private final IArchiveService archives;
    private final ArchiveCatalogService catalog;
    private final ArchiveSecretLevels secretLevels;
    private static final Logger log = LoggerFactory.getLogger(MobileArchiveSearchController.class);
    private static final int DRAFT_PAGE_SIZE = 20;

    private final ArchiveAttachmentIndexer attachmentIndex;
    private final ISysDictTypeService dictTypes;
    private final ArchiveFileService archiveFiles;

    public MobileArchiveSearchController(JdbcTemplate jdbc, ISysUserService users, ISysMenuService menus,
            IArchiveService archives, ArchiveCatalogService catalog, ArchiveSecretLevels secretLevels,
            ArchiveAttachmentIndexer attachmentIndex, ISysDictTypeService dictTypes, ArchiveFileService archiveFiles) {
        this.jdbc = jdbc;
        this.users = users;
        this.menus = menus;
        this.archives = archives;
        this.catalog = catalog;
        this.secretLevels = secretLevels;
        this.attachmentIndex = attachmentIndex;
        this.dictTypes = dictTypes;
        this.archiveFiles = archiveFiles;
    }

    @InitBinder("mobileSearch")
    public void bindSearch(WebDataBinder binder) {
        binder.setAllowedFields("keyword", "categoryId", "secretLevel", "dateFrom", "dateTo",
                "pageNum", "pageSize", "orderByColumn", "isAsc");
    }

    @Anonymous
    @GetMapping("/search")
    public ResponseEntity<AjaxResult> search(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @ModelAttribute("mobileSearch") ArchiveQuery query) {
        if (header == null || header.length() > 263 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            return rejected(401, "请重新登录");
        }
        if (jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema=database() and table_name in ('wx_token','wx_binding')
                """, Integer.class) != 2) {
            return rejected(503, "小程序账号服务尚未接入");
        }
        SysUser user = findMiniappUser(header);
        if (user == null || !"0".equals(user.getDelFlag())) return rejected(401, "请重新登录");
        if (!"0".equals(user.getStatus())) {
            AjaxResult result = AjaxResult.error("账号已停用");
            result.put("code", "ACCOUNT_DISABLED");
            return ResponseEntity.status(403).body(result);
        }
        var permissions = menus.selectPermsByUserId(user.getUserId());
        Predicate<String> allowed = permission -> user.isAdmin() || permissions.contains(permission);
        if (!allowed.test("archiveSearch") && !allowed.test("records:search:view")) {
            return rejected(403, "当前账号没有查档权限");
        }
        var categories = catalog.registrationChoices("", id -> allowed.test("archiveCategoryAll")
                || allowed.test("archiveCategory_" + id));
        query.setOwnerName(user.getLoginName());
        query.setAdmin(user.isAdmin());
        query.setAllowedCategoryIds(categories.stream().map(ArchiveCatalogService.Choice::id).toList());
        query.setMaxSecretLevel(secretLevels.maxAllowed(user.isAdmin(), level -> allowed.test("archiveSecret" + level)));
        if (query.getKeyword() != null && query.getKeyword().length() > 200) {
            return rejected(400, "关键词最长为200个字符");
        }
        boolean attachmentSearch = allowed.test("archiveFileView") && attachmentIndex.isAvailable();
        query.setSearchAttachments(attachmentSearch && query.getKeyword() != null && !query.getKeyword().isBlank());
        query.setPageSize(query.getPageSize() == null ? 20 : Math.min(50, query.getPageSize()));
        var rows = archives.selectArchiveList(query);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(AjaxResult.success(Map.of(
                "total", getDataTable(rows).getTotal(),
                "rows", rows.stream().map(ArchiveController.SearchRow::from).toList(),
                "categories", categories.stream().map(item -> Map.of("id", Long.toString(item.id()), "name", item.path())).toList(),
                "attachmentSearch", attachmentSearch)));
    }

    // 查询有效的小程序令牌。
    private SysUser findMiniappUser(String header) {
        if (header == null || header.length() > 263 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            return null;
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

    private record DraftScope(SysUser user, Predicate<String> allowed, LongPredicate categories, int maxSecret) {
        String actor() {
            return user.getLoginName();
        }
    }

    // 校验账号和操作权限。
    private DraftScope draftScope(String header, String... required) {
        SysUser user = findMiniappUser(header);
        if (user == null || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        }
        var permissions = menus.selectPermsByUserId(user.getUserId());
        Predicate<String> allowed = key -> user.isAdmin() || permissions.contains(key);
        if (!allowed.test("archiveQuery")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有档案登记权限");
        }
        for (String key : required) {
            if (!allowed.test(key)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此操作权限");
            }
        }
        LongPredicate categories = id -> allowed.test("archiveCategoryAll") || allowed.test("archiveCategory_" + id);
        int maxSecret = secretLevels.maxAllowed(user.isAdmin(), level -> allowed.test("archiveSecret" + level));
        return new DraftScope(user, allowed, categories, maxSecret);
    }

    // 分页查本人草稿。
    @GetMapping("/drafts")
    public ResponseEntity<AjaxResult> listDrafts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum) {
        var scope = draftScope(header);
        int page = Math.max(1, pageNum);
        var query = new ArchiveQuery();
        query.setPageNum(page);
        query.setPageSize(DRAFT_PAGE_SIZE);
        query.setOwnerName(scope.actor());
        query.setAdmin(false);
        query.setArchiveStatus("DRAFT");
        query.setMaxSecretLevel(scope.maxSecret());
        query.setAllowedCategoryIds(catalog.registrationChoices("", scope.categories()).stream()
                .map(ArchiveCatalogService.Choice::id).toList());

        var records = archives.selectArchiveList(query);
        long total = getDataTable(records).getTotal();
        var rows = records.stream().map(record -> Map.of(
                "archiveId", record.getArchiveId().toString(),
                "archiveCode", record.getArchiveCode(),
                "title", record.getTitle(),
                "formingDate", record.getFormingDate().toString()
        )).toList();
        return draftOk(Map.of("rows", rows, "hasMore", (long) page * DRAFT_PAGE_SIZE < total));
    }

    // 加载表单选项。
    @GetMapping("/drafts/options")
    public ResponseEntity<AjaxResult> draftOptions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header) {
        var scope = draftScope(header);
        var categories = catalog.registrationChoices("", scope.categories()).stream()
                .map(item -> Map.of("categoryId", Long.toString(item.id()), "name", item.path())).toList();
        var levels = secretLevels.options().entrySet().stream()
                .filter(item -> item.getKey() <= scope.maxSecret())
                .map(item -> Map.of("value", item.getKey(), "label", item.getValue())).toList();
        var items = dictTypes.selectDictDataByType("arc_retention_period");
        var retentions = items == null ? List.of() : items.stream()
                .filter(item -> "0".equals(item.getStatus()))
                .map(item -> Map.of("code", item.getDictValue(), "label", item.getDictLabel())).toList();
        return draftOk(Map.of("categories", categories, "secretLevels", levels, "retentions", retentions));
    }

    // 读取草稿和附件。
    @GetMapping("/drafts/{id}")
    public ResponseEntity<AjaxResult> draftDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @PathVariable("id") long id) {
        var scope = draftScope(header);
        var record = archives.selectDraftForEdit(id, scope.actor(), false, scope.categories(), scope.maxSecret());
        var data = new LinkedHashMap<>(savedDraft(record));
        data.put("title", record.getTitle());
        data.put("formingDate", record.getFormingDate().toString());
        data.put("categoryId", record.getCategoryId().toString());
        data.put("secretLevel", record.getSecretLevel());
        data.put("retentionCode", record.getRetentionCode());
        data.put("summary", record.getSummary() == null ? "" : record.getSummary());
        data.put("metadataJson", record.getMetadataJson());
        data.put("files", scope.allowed().test("archiveFileView")
                ? archiveFiles.list(id, scope.actor(), false, scope.categories(), scope.maxSecret())
                        .stream().map(this::fileView).toList()
                : List.of());
        return draftOk(data);
    }

    // 新建草稿。
    @PostMapping("/drafts")
    public ResponseEntity<AjaxResult> createDraft(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @RequestBody DraftInput input) {
        var scope = draftScope(header, "archiveAdd");
        var user = scope.user();
        if (user.getDeptId() == null || user.getDeptId() <= 0) {
            throw new ServiceException("请先配置账号所属部门");
        }
        return draftOk(savedDraft(archives.createDraft(input.toRecord(), scope.actor(), user.getUserId(),
                user.getDeptId(), scope.categories(), scope.maxSecret())));
    }

    // 按版本保存修改。
    @PutMapping("/drafts/{id}")
    public ResponseEntity<AjaxResult> updateDraft(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @PathVariable("id") long id, @RequestBody DraftInput input) {
        var scope = draftScope(header, "archiveEdit");
        var record = input.toRecord();
        record.setArchiveId(id);
        record.setVersion(requireVersion(input.version()));
        return draftOk(savedDraft(archives.updateDraft(record, scope.actor(), scope.user().getUserId(),
                false, scope.categories(), scope.maxSecret())));
    }

    // 按版本撤销草稿。
    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<AjaxResult> removeDraft(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @PathVariable("id") long id, @RequestParam("version") int version) {
        var scope = draftScope(header, "archiveDelete");
        archives.deleteArchive(id, requireVersion(version), scope.actor(), scope.user().getUserId(),
                false, scope.categories(), scope.maxSecret());
        return draftOk(Map.of("archiveId", Long.toString(id)));
    }

    // 上传单个附件。
    @PostMapping(value = "/drafts/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AjaxResult> uploadDraftFile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header,
            @PathVariable("id") long id, @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName)
            throws IOException, NoSuchAlgorithmException {
        var scope = draftScope(header, "archiveFileView", "archiveFileUpload");
        var saved = archiveFiles.upload(id, file, null, scope.actor(), scope.user().getUserId(),
                false, scope.categories(), scope.maxSecret(), fileName);
        return draftOk(fileView(saved));
    }

    public record DraftInput(String title, LocalDate formingDate, Long categoryId, Integer secretLevel,
                             String retentionCode, String summary, String metadataJson, Integer version) {
        ArchiveRecord toRecord() {
            var record = new ArchiveRecord();
            record.setTitle(title == null ? null : title.strip());
            record.setFormingDate(formingDate);
            record.setCategoryId(categoryId);
            record.setSecretLevel(secretLevel);
            record.setRetentionCode(retentionCode);
            record.setSummary(summary);
            record.setMetadataJson(metadataJson);
            return record;
        }
    }

    private int requireVersion(Integer version) {
        if (version == null || version < 1) throw new ServiceException("请重新打开草稿");
        return version;
    }

    private Map<String, Object> savedDraft(ArchiveRecord record) {
        return Map.of("archiveId", record.getArchiveId().toString(),
                "archiveCode", record.getArchiveCode(), "version", record.getVersion());
    }

    private Map<String, Object> fileView(ArchiveFile file) {
        return Map.of("fileId", file.getFileId().toString(),
                "displayName", file.getDisplayName(), "fileSize", file.getFileSize());
    }

    private ResponseEntity<AjaxResult> draftOk(Object data) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(AjaxResult.success(data));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AjaxResult> draftAccessError(ResponseStatusException error) {
        return rejected(error.getStatusCode().value(), error.getReason());
    }

    // 隐藏内部错误细节。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AjaxResult> unexpectedError(Exception error) {
        if (error instanceof HttpMessageNotReadableException) {
            return rejected(400, "表单格式不正确");
        }
        if (error instanceof ErrorResponse requestError && requestError.getStatusCode().is4xxClientError()) {
            int status = requestError.getStatusCode().value();
            return rejected(status, status == 413 ? "文件超过大小限制" : "请求参数不正确");
        }
        log.error("处理小程序档案请求失败", error);
        return rejected(500, "档案操作暂时失败");
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> invalidQuery(ServiceException error) {
        return rejected(400, error.getMessage());
    }

    private ResponseEntity<AjaxResult> rejected(int status, String message) {
        AjaxResult result = AjaxResult.error(message);
        result.put("code", status);
        return ResponseEntity.status(status).body(result);
    }
}
