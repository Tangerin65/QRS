package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.domain.ArchiveBatchEdit;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.service.ArchiveAttachmentIndexer;
import com.ruoyi.system.service.ArchiveCatalogService.Choice;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.IArchiveService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ArchiveSecretLevels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/*M01的Router*/
@Controller
@RequestMapping("/records")
public class ArchiveController extends BaseController {
    private static final String QUERY = "archiveQuery";
    private static final String SEARCH = "archiveSearch";
    private final IArchiveService archives;/*档案业务*/
    private final ArchiveCatalogService catalog;/*分类*/
    private final ISysDictTypeService dictTypes;/*字典*/
    private final ArchiveSecretLevels secretLevels;
    private final ArchiveAttachmentIndexer attachmentIndex;
    private final ArchiveSearchPlans searchPlans;

    private record AccessScope(LongPredicate categoryAllowed, List<Long> categoryIds, int maxSecretLevel) {}

    public ArchiveController(IArchiveService archives, ArchiveCatalogService catalog, ISysDictTypeService dictTypes,
                             ArchiveSecretLevels secretLevels, ArchiveAttachmentIndexer attachmentIndex,
                             ArchiveSearchPlans searchPlans) {
        this.archives = archives;
        this.catalog = catalog;
        this.dictTypes = dictTypes;
        this.secretLevels = secretLevels;
        this.attachmentIndex = attachmentIndex;
        this.searchPlans = searchPlans;
    }

    private AccessScope accessScope() {
        //分类权限与数字密级范围独立于字典名称和排序
        var subject = SecurityUtils.getSubject();
        boolean admin = ShiroUtils.isAdmin(getUserId());//1管理员权限
        List<Long> categoryIds = new ArrayList<>();
        for (Choice choice : catalog.registrationChoices("", id -> admin || subject.isPermitted("archiveCategoryAll") || subject.isPermitted("archiveCategory_" + id)))
            categoryIds.add(choice.id());
        int maxSecretLevel = secretLevels.maxAllowed(admin, level -> subject.isPermitted("archiveSecret" + level));
        return new AccessScope(id -> admin || subject.isPermitted("archiveCategoryAll") || subject.isPermitted("archiveCategory_" + id), categoryIds, maxSecretLevel);
    }

    private ArchiveQuery scopedQuery(ArchiveQuery query) {
        //把用户信息&权限写入查询对象，给service层使用
        AccessScope scope = accessScope();
        query.setOwnerName(getLoginName());
        query.setAdmin(ShiroUtils.isAdmin(getUserId()));
        query.setAllowedCategoryIds(scope.categoryIds());
        query.setMaxSecretLevel(scope.maxSecretLevel());
        query.setSearchAttachments(false);
        return query;
    }

    private ArchiveQuery scopedSearch(ArchiveQuery query) {
        scopedQuery(query);
        String keyword = query.getKeyword();
        if (keyword == null || keyword.isBlank()) return query;
        if (keyword.length() > 200) throw new ServiceException("关键词最长为200个字符");
        query.setSearchAttachments(canSearchAttachments());
        return query;
    }

    private boolean canSearchAttachments() {
        return SecurityUtils.getSubject().isPermitted("archiveFileView") && attachmentIndex.isAvailable();
    }

    @InitBinder("searchQuery")
    public void bindSearchQuery(WebDataBinder binder) {
        binder.setAllowedFields("archiveCode", "title", "keyword", "categoryId", "secretLevel",
                "archiveStatus", "dateFrom", "dateTo", "pageNum", "pageSize", "orderByColumn", "isAsc");
    }

    @GetMapping("/search")
    @RequiresPermissions(SEARCH)
    public String searchPage(Model model) {
        var subject = SecurityUtils.getSubject();
        var session = subject.getSession();
        if (session.getAttribute(ShiroConstants.CSRF_TOKEN) == null) {
            session.setAttribute(ShiroConstants.CSRF_TOKEN, ServletUtils.generateToken());
        }
        model.addAttribute("csrfToken", session.getAttribute(ShiroConstants.CSRF_TOKEN));
        AccessScope scope = accessScope();
        model.addAttribute("categories", catalog.registrationChoices("", scope.categoryAllowed()));
        model.addAttribute("secretOptions", secretLevels.options());
        model.addAttribute("canView", subject.isPermitted("archiveView"));
        model.addAttribute("canSearchAttachments", canSearchAttachments());
        return "archive/search";
    }

    @PostMapping("/search/list")
    @ResponseBody
    @RequiresPermissions(SEARCH)
    public TableDataInfo searchList(@ModelAttribute("searchQuery") ArchiveQuery query) {
        List<ArchiveRecord> rows = archives.selectArchiveList(scopedSearch(query));
        TableDataInfo table = getDataTable(rows);
        table.setRows(rows.stream().map(SearchRow::from).toList());
        return table;
    }

    record SearchRow(String archiveId, String archiveCode, String title, String categoryName,
            LocalDate formingDate, String secretName, String archiveStatus, String summary, String createBy,
            String matchedAttachment, String attachmentExcerpt) {
        static SearchRow from(ArchiveRecord record) {
            return new SearchRow(record.getArchiveId().toString(), record.getArchiveCode(), record.getTitle(),
                    record.getCategoryName(), record.getFormingDate(), record.getSecretName(),
                    record.getArchiveStatus(), record.getSummary(), record.getCreateBy(),
                    record.getMatchedAttachment(), record.getAttachmentExcerpt());
        }
    }

    @GetMapping("/search/export")
    @RequiresPermissions({SEARCH, "archiveExport"})
    @Log(title = "档案检索", businessType = BusinessType.EXPORT)
    public void exportSearch(@ModelAttribute("searchQuery") ArchiveQuery query, HttpServletResponse response) throws IOException {
        writeExport(scopedSearch(query), response);
    }

    @PostMapping("/search/statistics")
    @ResponseBody
    @RequiresPermissions(SEARCH)
    public AjaxResult searchStatistics(@ModelAttribute("searchQuery") ArchiveQuery query) {
        return AjaxResult.success(archives.statistics(scopedSearch(query)));
    }

    @GetMapping("/search/print")
    @RequiresPermissions(SEARCH)
    public String printSearch(@ModelAttribute("searchQuery") ArchiveQuery query, Model model, HttpServletResponse response) {
        scopedSearch(query);
        query.setMaxRows(1000);
        model.addAttribute("rows", archives.selectArchiveList(query));
        model.addAttribute("statistics", archives.statistics(query));
        response.setHeader("Cache-Control", "no-store");
        return "archive/search-print";
    }

    @GetMapping("/search/plans")
    @ResponseBody
    @RequiresPermissions(SEARCH)
    public AjaxResult plans() {
        return AjaxResult.success(searchPlans.list(getUserId()));
    }

    @PostMapping("/search/plans")
    @ResponseBody
    @RequiresPermissions(SEARCH)
    public AjaxResult savePlan(@RequestParam(required = false) Long planId, @RequestParam String name,
            @ModelAttribute("searchQuery") ArchiveQuery query) {
        searchPlans.save(getUserId(), planId, name, query);
        return success();
    }

    @PostMapping("/search/plans/{id}/disable")
    @ResponseBody
    @RequiresPermissions(SEARCH)
    public AjaxResult disablePlan(@PathVariable long id) {
        return searchPlans.disable(getUserId(), id) ? success() : error("方案不存在或已停用");
    }

    @GetMapping("/register")
    @RequiresPermissions(QUERY)
    public String page(Model model) {
        //加载档案登记页面与操作权限
        var session = SecurityUtils.getSubject().getSession();
        if (session.getAttribute(ShiroConstants.CSRF_TOKEN) == null)
            session.setAttribute(ShiroConstants.CSRF_TOKEN, ServletUtils.generateToken());
        var subject = SecurityUtils.getSubject();
        model.addAttribute("csrfToken", session.getAttribute(ShiroConstants.CSRF_TOKEN));
        model.addAttribute("canAdd", subject.isPermitted("archiveAdd"));
        model.addAttribute("canEdit", subject.isPermitted("archiveEdit"));
        model.addAttribute("canSubmit", subject.isPermitted("archiveSubmit"));
        model.addAttribute("canDelete", subject.isPermitted("archiveDelete"));
        model.addAttribute("canImport", subject.isPermitted("archiveImport"));
        model.addAttribute("canExport", subject.isPermitted("archiveExport"));
        model.addAttribute("canView", subject.isPermitted("archiveView"));
        model.addAttribute("canFiles", subject.isPermitted("archiveFileView"));
        model.addAttribute("retentionOptions", dictTypes.selectDictDataByType("arc_retention_period"));
        model.addAttribute("secretOptions", secretLevels.options());
        return "archive/register";//返回页面
    }

    @PostMapping("/list")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public TableDataInfo list(ArchiveQuery query) {
        //查询表格
        scopedQuery(query);
        return getDataTable(archives.selectArchiveList(query));
    }

    @GetMapping("/detail/{archiveId}")
    @ResponseBody
    @RequiresPermissions("archiveView")
    public AjaxResult detail(@PathVariable long archiveId) {
        //调出完整档案
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.selectArchiveDetail(archiveId, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/add")
    @ResponseBody
    @RequiresPermissions("archiveAdd")
    @Log(title = "档案登记", businessType = BusinessType.INSERT)
    public AjaxResult add(@RequestBody ArchiveRecord record) {
        var user = getSysUser();
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.createDraft(record, getLoginName(), getUserId(), user.getDeptId(),
            scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @GetMapping("/edit/{archiveId}")
    @ResponseBody
    @RequiresPermissions("archiveEdit")
    public AjaxResult editDetail(@PathVariable long archiveId) {
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.selectDraftForEdit(archiveId, getLoginName(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/edit")
    @ResponseBody
    @RequiresPermissions("archiveEdit")
    @Log(title = "档案登记", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@RequestBody ArchiveRecord record) {
        //保存修改
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.updateDraft(record, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/submit")
    @ResponseBody
    @RequiresPermissions("archiveSubmit")
    @Log(title = "档案登记", businessType = BusinessType.UPDATE)
    public AjaxResult submit(long archiveId, int version) {
        AccessScope scope = accessScope();
        archives.submitDraft(archiveId, version, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel());
        return success();
    }

    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("archiveDelete")
    @Log(title = "档案登记", businessType = BusinessType.DELETE)
    public AjaxResult remove(long archiveId, int version) {
        AccessScope scope = accessScope();
        archives.deleteDraft(archiveId, version, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()),
            scope.categoryAllowed(), scope.maxSecretLevel());
        return success();
    }

    @GetMapping("/categories")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult categories(String keyword) {
        AccessScope scope = accessScope();
        return AjaxResult.success(catalog.registrationChoices(keyword, scope.categoryAllowed()));
    }

    @GetMapping("/code-preview")
    @ResponseBody
    @RequiresPermissions("archiveAdd")
    public AjaxResult previewCode(long categoryId, LocalDate formingDate) {
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.previewCode(categoryId, formingDate, scope.categoryAllowed()));
    }

    @PostMapping("/statistics")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult statistics(ArchiveQuery query) {
        scopedQuery(query);
        return AjaxResult.success(archives.statistics(query));
    }

    @PostMapping("/import/preview")
    @ResponseBody
    @RequiresPermissions("archiveImport")
    public AjaxResult previewImport(@RequestParam MultipartFile file) {
        AccessScope scope = accessScope();
        return AjaxResult.success(archives.validateImport(file, scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/import/confirm")
    @ResponseBody
    @RequiresPermissions("archiveImport")
    @Log(title = "档案登记", businessType = BusinessType.IMPORT)
    public AjaxResult confirmImport(@RequestBody List<ArchiveRecord> records) {
        var user = getSysUser();
        AccessScope scope = accessScope();
        return AjaxResult.success("已导入 " + archives.importDrafts(records, getLoginName(), getUserId(), user.getDeptId(),
            scope.categoryAllowed(), scope.maxSecretLevel()) + " 条档案");
    }

    @PostMapping("/batch-edit")
    @ResponseBody
    @RequiresPermissions("archiveEdit")
    @Log(title = "档案登记", businessType = BusinessType.UPDATE)
    public AjaxResult batchEdit(@RequestBody ArchiveBatchEdit edit) {
        AccessScope scope = accessScope();
        return AjaxResult.success("已批量修改 " + archives.batchEdit(edit, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId()), scope.categoryAllowed(), scope.maxSecretLevel()) + " 条草稿");
    }

    @GetMapping("/import/template")
    @RequiresPermissions("archiveImport")
    public void template(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=archive-template.xlsx");
        ArchiveWorkbook.writeTemplate(response.getOutputStream(), secretLevels.options());
    }

    @GetMapping("/export")
    @RequiresPermissions("archiveExport")
    @Log(title = "档案登记", businessType = BusinessType.EXPORT)
    public void export(ArchiveQuery query, HttpServletResponse response) throws IOException {
        writeExport(scopedQuery(query), response);
    }

    private void writeExport(ArchiveQuery query, HttpServletResponse response) throws IOException {
        query.setMaxRows(10000);
        List<ArchiveRecord> rows = archives.selectArchiveList(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=archives.xlsx");
        ArchiveWorkbook.writeArchives(response.getOutputStream(), rows);
    }
}
