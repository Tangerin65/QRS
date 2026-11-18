package com.ruoyi.system.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveFile;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.mapper.ArchiveFileMapper;
import com.ruoyi.system.mapper.ArchiveMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArchiveFileService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "markdown", "bmp", "gif", "jpg", "jpeg", "png", "tif", "tiff", "webp",
        "mp3", "wav", "wma", "aac", "flac", "ogg", "m4a", "mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "mpg", "mpeg", "rmvb");
    private final ArchiveFileMapper files;
    private final ArchiveMapper archives;
    private final Path root;

    public ArchiveFileService(ArchiveFileMapper files, ArchiveMapper archives,
        @Value("${archive.file-root}") String fileRoot) {
        //初始化附件数据访问与存储目录
        this.files = files;
        this.archives = archives;
        this.root = Path.of(fileRoot).toAbsolutePath().normalize();
    }

    public ArchiveRecord requireArchive(long archiveId, String actor, boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) {
        //校验档案访问权限
        ArchiveRecord record = archives.selectArchiveById(archiveId, actor, admin);
        if (record == null || !categoryAllowed.test(record.getCategoryId()) || record.getSecretLevel() > maxSecretLevel)
            throw new ServiceException("档案不存在或无权访问");
        return record;
    }

    public List<ArchiveFile> list(long archiveId, String actor, boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) {
        //查询有权访问的档案附件
        requireArchive(archiveId, actor, admin, categoryAllowed, maxSecretLevel);
        return files.selectFiles(archiveId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArchiveFile upload(long archiveId, MultipartFile upload, String relativePath, String actor, long actorId,
        boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) throws IOException, NoSuchAlgorithmException {
        //上传单个附件并生成唯一存储名称
        //锁住档案行，避免上传期间档案被修改
        if (files.lockArchive(archiveId) == null) throw new ServiceException("档案不存在或无权访问");
        ArchiveRecord archive = requireArchive(archiveId, actor, admin, categoryAllowed, maxSecretLevel);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿可以上传附件");
        if (upload.isEmpty()) throw new ServiceException("请选择非空文件");
        String name = upload.getOriginalFilename();
        name = name == null ? "" : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255) throw new ServiceException("文件名须为1至255个字符");
        String logicalPath = normalizePath(relativePath == null || relativePath.isBlank() ? name : relativePath, name);
        String type = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) throw new ServiceException("支持PDF、Word、Excel、PowerPoint、TXT、Markdown、图片及音视频文件");
        String storeKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/"
            + UUID.randomUUID().toString().replace("-", "") + "." + type;
        Path target = root.resolve(storeKey);
        Files.createDirectories(target.getParent());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            //事务未提交时清理本次创建的文件，避免数据库失败留下孤立附件
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    //回滚时清理附件文件
                    if (status != STATUS_COMMITTED) FileSystemUtils.deleteRecursively(target.toFile());
                }
            });
            try (var input = new DigestInputStream(upload.getInputStream(), digest)) {
                input.transferTo(output);
            }
        }
        ArchiveFile file = new ArchiveFile();
        file.setArchiveId(archiveId);
        file.setDisplayName(name);
        file.setRelativePath(logicalPath);
        file.setStoreKey(storeKey);
        file.setFileType(type);
        file.setFileSize(upload.getSize());
        file.setSha256(HexFormat.of().formatHex(digest.digest()));
        file.setCreateBy(actor);
        files.insertFile(file);
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_UPLOAD", actorId, actor, "上传附件：" + name);
        return file;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long archiveId, long fileId, String actor, long actorId, boolean admin,
        LongPredicate categoryAllowed, int maxSecretLevel) {
        //只删除附件数据库记录并保留磁盘文件
        if (files.lockArchive(archiveId) == null) throw new ServiceException("档案不存在或无权访问");
        ArchiveRecord archive = requireArchive(archiveId, actor, admin, categoryAllowed, maxSecretLevel);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿档案的附件可以删除");
        if (files.deleteFile(fileId, archiveId) == 0) throw new ServiceException("附件不存在");
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_DELETE", actorId, actor, "删除附件记录：" + fileId);
    }

    private String normalizePath(String value, String name) {
        //保留文件夹逻辑层级并拒绝越级路径
        String path = value.replace('\\', '/');
        if (path.length() > 1024 || path.startsWith("/") || path.indexOf(':') >= 0
            || path.chars().anyMatch(Character::isISOControl))
            throw new ServiceException("文件路径无效");
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) throw new ServiceException("文件路径无效");
        }
        if (!parts[parts.length - 1].equals(name)) throw new ServiceException("相对路径与文件名不一致");
        return path;
    }
}
