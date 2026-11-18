package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.service.ArchiveFileService;
import com.ruoyi.system.service.ArchiveSecretLevels;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.function.LongPredicate;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/records/files")
public class ArchiveFileController extends BaseController {
    private final ArchiveFileService files;
    private final ArchiveSecretLevels secretLevels;
    private final long maxFileSize;
    private record AccessScope(boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) {}

    public ArchiveFileController(ArchiveFileService files, ArchiveSecretLevels secretLevels,
                                 @Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        //初始化附件服务与上传大小配置，M02大文件上传要做修改
        this.files = files;
        this.secretLevels = secretLevels;
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();
    }

    private AccessScope accessScope() {
        //沿用档案所有权及密级授权
        var subject = SecurityUtils.getSubject();
        boolean admin = ShiroUtils.isAdmin(getUserId());
        int maxSecretLevel = secretLevels.maxAllowed(admin, level -> subject.isPermitted("archiveSecret" + level));
        return new AccessScope(admin, id -> admin || subject.isPermitted("archiveCategoryAll")
            || subject.isPermitted("archiveCategory_" + id), maxSecretLevel);
    }

    @GetMapping("/{archiveId}")
    @RequiresPermissions("archiveFileView")
    public String page(@PathVariable long archiveId, Model model) {
        //打开档案附件页面
        AccessScope scope = accessScope();
        var archive = files.requireArchive(archiveId, getLoginName(), scope.admin(), scope.categoryAllowed(), scope.maxSecretLevel());
        model.addAttribute("archive", archive);
        model.addAttribute("canUpload", "DRAFT".equals(archive.getArchiveStatus())
            && SecurityUtils.getSubject().isPermitted("archiveFileUpload"));
        model.addAttribute("canDeleteFiles", "DRAFT".equals(archive.getArchiveStatus())
            && SecurityUtils.getSubject().isPermitted("archiveFileView")
            && SecurityUtils.getSubject().isPermitted("archiveFileDelete"));
        model.addAttribute("maxFileSize", maxFileSize);
        return "archive/files";
    }

    @GetMapping("/{archiveId}/list")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult list(@PathVariable long archiveId) {
        //返回档案附件清单
        AccessScope scope = accessScope();
        return AjaxResult.success(files.list(archiveId, getLoginName(), scope.admin(), scope.categoryAllowed(), scope.maxSecretLevel()));
    }

    @PostMapping("/{archiveId}/delete/{fileId}")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileDelete"})
    @Log(title = "档案附件记录删除", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable long archiveId, @PathVariable long fileId) {
        //删除附件数据库记录并保留磁盘文件
        AccessScope scope = accessScope();
        files.delete(archiveId, fileId, getLoginName(), getUserId(), scope.admin(), scope.categoryAllowed(), scope.maxSecretLevel());
        return AjaxResult.success();
    }

    @PostMapping("/{archiveId}/upload")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileUpload"})
    @Log(title = "档案附件上传", businessType = BusinessType.INSERT)
    public AjaxResult upload(@PathVariable long archiveId, @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String relativePath) throws IOException, NoSuchAlgorithmException {
        //接收单文件或上传队列中的一个文件
        AccessScope scope = accessScope();
        return AjaxResult.success(files.upload(archiveId, file, relativePath, getLoginName(), getUserId(),
            scope.admin(), scope.categoryAllowed(), scope.maxSecretLevel()));
    }
}
