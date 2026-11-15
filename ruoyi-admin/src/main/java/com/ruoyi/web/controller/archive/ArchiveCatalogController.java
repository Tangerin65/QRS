package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveCatalogService.Change;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/archive/catalog")
public class ArchiveCatalogController extends BaseController {
    private static final String VIEW = "arc:category:view";
    private static final String MANAGE = "arc:category:manage";
    private final ArchiveCatalogService catalog;

    public ArchiveCatalogController(ArchiveCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @RequiresPermissions(VIEW)
    public String page(Model model) {
        var subject = SecurityUtils.getSubject();
        var session = subject.getSession();
        if (session.getAttribute(ShiroConstants.CSRF_TOKEN) == null) {
            session.setAttribute(ShiroConstants.CSRF_TOKEN, ServletUtils.generateToken());
        }
        model.addAttribute("canManage", subject.isPermitted(MANAGE));
        return "archive/catalog";
    }

    @GetMapping("/entries")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult entries(@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String kind,
                              @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String sort) {
        return AjaxResult.success(catalog.list(keyword, kind, status, sort));
    }

    @GetMapping("/entries/{id}")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult detail(@PathVariable long id) {
        return AjaxResult.success(catalog.get(id));
    }

    @PostMapping("/entries")
    @ResponseBody
    @RequiresPermissions(value = {VIEW, MANAGE}, logical = Logical.AND)
    @Log(title = "档案分类管理", businessType = BusinessType.UPDATE)
    public AjaxResult save(@RequestBody Change input) {
        return AjaxResult.success(catalog.save(input, getLoginName()));
    }

    @GetMapping("/export")
    @RequiresPermissions(VIEW)
    @Log(title = "档案分类管理", businessType = BusinessType.EXPORT)
    public void export(@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String kind,
                       @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String sort,
                       HttpServletResponse response) throws IOException {
        sendWorkbook(response, "archive-catalog.xlsx", false, catalog.list(keyword, kind, status, sort));
    }

    @GetMapping("/form")
    @RequiresPermissions(value = {VIEW, MANAGE}, logical = Logical.AND)
    public void template(HttpServletResponse response) throws IOException {
        sendWorkbook(response, "archive-catalog-template.xlsx", true, List.of());
    }

    @PostMapping("/import")
    @ResponseBody
    @RequiresPermissions(value = {VIEW, MANAGE}, logical = Logical.AND)
    @Log(title = "档案分类管理", businessType = BusinessType.IMPORT)
    public AjaxResult importFile(@RequestParam MultipartFile file) {
        return AjaxResult.success("已导入 " + catalog.importRows(ArchiveCatalogWorkbook.read(file), getLoginName()) + " 条目录。");
    }

    private void sendWorkbook(HttpServletResponse response, String name, boolean template,
                              List<ArchiveCatalogService.Catalog> entries) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + name);
        var all = template ? List.<ArchiveCatalogService.Catalog>of() : catalog.list(null, null, null, null);
        ArchiveCatalogWorkbook.write(response.getOutputStream(), entries, all, template);
    }
}
