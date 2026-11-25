package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveMetaTemplateService;
import com.ruoyi.system.service.ArchiveMetaTemplateService.Change;
import com.ruoyi.system.service.ArchiveMetaTemplateService.Preview;
import java.util.function.LongPredicate;
import org.apache.shiro.SecurityUtils;
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

@Controller
@RequestMapping("/archive/meta-template")
public class ArchiveMetaTemplateController extends BaseController {
    private static final String VIEW = "arc:meta:view";
    private static final String MANAGE = "arc:meta:manage";
    private final ArchiveCatalogService catalog;
    private final ArchiveMetaTemplateService templates;

    public ArchiveMetaTemplateController(ArchiveCatalogService catalog, ArchiveMetaTemplateService templates) {
        this.catalog = catalog;
        this.templates = templates;
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
        model.addAttribute("categories", catalog.registrationChoices("", categoryScope()));
        return "archive/meta-template";
    }

    @GetMapping("/categories/{categoryId}")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult get(@PathVariable long categoryId, @RequestParam(required = false) Integer version) {
        catalog.requireRegistrationCategory(categoryId, categoryScope());
        return AjaxResult.success(templates.get(categoryId, version));
    }

    @PostMapping("/categories/{categoryId}")
    @ResponseBody
    @RequiresPermissions({VIEW, MANAGE})
    @Log(title = "元数据模板", businessType = BusinessType.UPDATE)
    public AjaxResult save(@PathVariable long categoryId, @RequestBody Change change) {
        return AjaxResult.success(templates.save(categoryId, change, getLoginName(), categoryScope()));
    }

    @PostMapping("/categories/{categoryId}/validate")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult validate(@PathVariable long categoryId, @RequestBody Preview preview) {
        catalog.requireRegistrationCategory(categoryId, categoryScope());
        // 预览使用页面提交的字段定义，未保存的改动也可以试填。
        return AjaxResult.success(templates.validateValues(preview));
    }

    private LongPredicate categoryScope() {
        // 分类范围沿用档案登记的权限标识，模板维护权限由菜单单独授予。
        var subject = SecurityUtils.getSubject();
        boolean all = ShiroUtils.isAdmin(getUserId()) || subject.isPermitted("archiveCategoryAll");
        return id -> all || subject.isPermitted("archiveCategory_" + id);
    }
}
