package com.ruoyi.web.controller.archive;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.domain.ArchiveFileVersion;
import com.ruoyi.system.service.ArchiveFileService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final long maxFileSize;

    public ArchiveFileController(ArchiveFileService files,
                                 @Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        this.files = files;
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();
    }

    @GetMapping("/{archiveId}")
    @RequiresPermissions("archiveFileView")
    public String page(@PathVariable long archiveId, Model model) {
        var archive = files.requireArchive(archiveId, getLoginName(), ShiroUtils.isAdmin(getUserId()));
        var subject = SecurityUtils.getSubject();
        model.addAttribute("archive", archive);
        model.addAttribute("canUpload", "DRAFT".equals(archive.getArchiveStatus())
            && subject.isPermitted("archiveFileUpload"));
        model.addAttribute("canDeleteFiles", "DRAFT".equals(archive.getArchiveStatus())
            && subject.isPermitted("archiveFileDelete"));
        model.addAttribute("canPreview", subject.isPermitted("archiveFilePreview"));
        model.addAttribute("canDownload", subject.isPermitted("archiveFileDownload"));
        model.addAttribute("canPrint", subject.isPermitted("archiveFilePrint"));
        model.addAttribute("maxFileSize", maxFileSize);
        return "archive/files";
    }

    @GetMapping("/{archiveId}/list")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult list(@PathVariable long archiveId) {
        // 兼容原有 Service 签名，分类与密级参数不参与访问判断。
        return AjaxResult.success(files.list(archiveId, getLoginName(), ShiroUtils.isAdmin(getUserId()), id -> true, 0));
    }

    @PostMapping("/{archiveId}/delete/{fileId}")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileDelete"})
    @Log(title = "档案附件记录删除", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable long archiveId, @PathVariable long fileId) {
        files.delete(archiveId, fileId, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()), id -> true, 0);
        return AjaxResult.success();
    }

    @PostMapping("/{archiveId}/upload")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileUpload"})
    @Log(title = "档案附件上传", businessType = BusinessType.INSERT)
    public AjaxResult upload(@PathVariable long archiveId, @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String relativePath) throws IOException, NoSuchAlgorithmException {
        return AjaxResult.success(files.upload(archiveId, file, relativePath, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId()), id -> true, 0));
    }

    @PostMapping("/{archiveId}/statistics")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult statistics(@PathVariable long archiveId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) String archiveStatus,
        @RequestParam(defaultValue = "eventType") String groupBy) {
        return AjaxResult.success(files.usageStatistics(archiveId, dateFrom, dateTo, archiveStatus,
            groupBy, getLoginName(), ShiroUtils.isAdmin(getUserId())));
    }

    @GetMapping("/{archiveId}/file/{fileId}/versions/page")
    @RequiresPermissions("archiveFileView")
    public String versionsPage(@PathVariable long archiveId, @PathVariable long fileId, Model model) {
        // 验证档案访问权和附件归属后再打开页面。
        files.versions(archiveId, fileId, getLoginName(), ShiroUtils.isAdmin(getUserId()));
        var archive = files.requireArchive(archiveId, getLoginName(), ShiroUtils.isAdmin(getUserId()));
        var subject = SecurityUtils.getSubject();
        model.addAttribute("canRollback", "DRAFT".equals(archive.getArchiveStatus())
            && subject.isPermitted("archiveFileUpload"));
        model.addAttribute("canPreview", subject.isPermitted("archiveFilePreview"));
        model.addAttribute("canDownload", subject.isPermitted("archiveFileDownload"));
        model.addAttribute("canPrint", subject.isPermitted("archiveFilePrint"));
        model.addAttribute("archiveId", archiveId);
        model.addAttribute("fileId", fileId);
        return "archive/file-versions";
    }

    @GetMapping("/{archiveId}/file/{fileId}/versions")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult versions(@PathVariable long archiveId, @PathVariable long fileId) {
        return AjaxResult.success(files.versions(archiveId, fileId, getLoginName(), ShiroUtils.isAdmin(getUserId())));
    }

    @GetMapping("/{archiveId}/file/{fileId}/operations")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult operations(@PathVariable long archiveId, @PathVariable long fileId) {
        return AjaxResult.success(files.operations(archiveId, fileId, getLoginName(), ShiroUtils.isAdmin(getUserId())));
    }

    @PostMapping("/{archiveId}/file/{fileId}/versions")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileUpload"})
    @Log(title = "档案附件新增版本", businessType = BusinessType.INSERT)
    public AjaxResult addVersion(@PathVariable long archiveId, @PathVariable long fileId,
        @RequestParam("file") MultipartFile file) throws IOException, NoSuchAlgorithmException {
        return AjaxResult.success(files.addVersion(archiveId, fileId, file, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId())));
    }

    @PostMapping("/{archiveId}/file/{fileId}/rollback/{versionNo}")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileUpload"})
    @Log(title = "档案附件版本回退", businessType = BusinessType.UPDATE)
    public AjaxResult rollback(@PathVariable long archiveId, @PathVariable long fileId,
        @PathVariable int versionNo) {
        return AjaxResult.success(files.rollback(archiveId, fileId, versionNo, getLoginName(), getUserId(),
            ShiroUtils.isAdmin(getUserId())));
    }

    @GetMapping("/{archiveId}/file/{fileId}/compare")
    @ResponseBody
    @RequiresPermissions("archiveFileView")
    public AjaxResult compare(@PathVariable long archiveId, @PathVariable long fileId,
        @RequestParam int leftVersionNo, @RequestParam int rightVersionNo) {
        return AjaxResult.success(files.compare(archiveId, fileId, leftVersionNo, rightVersionNo,
            getLoginName(), ShiroUtils.isAdmin(getUserId())));
    }

    @PostMapping("/{archiveId}/file/{fileId}/revoke")
    @ResponseBody
    @RequiresPermissions({"archiveFileView", "archiveFileDelete"})
    @Log(title = "档案附件撤销", businessType = BusinessType.UPDATE)
    public AjaxResult revoke(@PathVariable long archiveId, @PathVariable long fileId,
        @RequestParam(required = false, defaultValue = "") String reason) {
        files.revoke(archiveId, fileId, reason, getLoginName(), getUserId(), ShiroUtils.isAdmin(getUserId()));
        return AjaxResult.success();
    }

    @GetMapping("/{archiveId}/file/{fileId}/version/{versionNo}/preview")
    @RequiresPermissions({"archiveFileView", "archiveFilePreview"})
    public ResponseEntity<Resource> preview(@PathVariable long archiveId,
        @PathVariable long fileId, @PathVariable int versionNo) throws IOException {
        return fileResponse(archiveId, fileId, versionNo, false, "FILE_PREVIEW");
    }

    @GetMapping("/{archiveId}/file/{fileId}/version/{versionNo}/download")
    @RequiresPermissions({"archiveFileView", "archiveFileDownload"})
    public ResponseEntity<Resource> download(@PathVariable long archiveId,
        @PathVariable long fileId, @PathVariable int versionNo) throws IOException {
        return fileResponse(archiveId, fileId, versionNo, true, "FILE_DOWNLOAD");
    }

    @GetMapping("/{archiveId}/file/{fileId}/version/{versionNo}/print")
    @RequiresPermissions({"archiveFileView", "archiveFilePrint"})
    public ResponseEntity<Resource> print(@PathVariable long archiveId,
        @PathVariable long fileId, @PathVariable int versionNo) throws IOException {
        return fileResponse(archiveId, fileId, versionNo, false, "FILE_PRINT");
    }

    private ResponseEntity<Resource> fileResponse(long archiveId, long fileId,
        int versionNo, boolean attachment, String eventType) throws IOException {
        boolean admin = ShiroUtils.isAdmin(getUserId());
        Path path = files.resolveVersion(archiveId, fileId, versionNo, getLoginName(), admin);
        ArchiveFileVersion version = files.version(archiveId, fileId, versionNo, getLoginName(), admin);
        String filename = URLEncoder.encode(version.getOriginalName(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String contentType = Files.probeContentType(path);
        MediaType type = contentType == null ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(contentType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentLength(Files.size(path));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, (attachment ? "attachment" : "inline")
            + "; filename*=UTF-8''" + filename);
        headers.set("X-Content-Type-Options", "nosniff");
        // 记录本次利用请求
        files.recordUse(archiveId, fileId, versionNo, eventType, getLoginName(), getUserId(), admin);
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
    }
}
