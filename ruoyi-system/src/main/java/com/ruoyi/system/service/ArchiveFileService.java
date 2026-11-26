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
import com.ruoyi.system.domain.ArchiveFileVersion;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;

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

    public ArchiveRecord requireArchive(long archiveId, String actor, boolean admin) {
        //校验档案是否存在及当前用户的基本访问权限
        ArchiveRecord record = archives.selectArchiveById(archiveId, actor, admin);
        if (record == null) throw new ServiceException("档案不存在或无权访问");
        return record;
    }

    public ArchiveRecord requireArchive(long archiveId, String actor, boolean admin,
                                        LongPredicate categoryAllowed, int maxSecretLevel) {
        //兼容原有调用，不再校验分类和密级
        return requireArchive(archiveId, actor, admin);
    }

    public List<ArchiveFile> list(long archiveId, String actor, boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) {
        //查询有权访问的档案附件
        requireArchive(archiveId, actor, admin, categoryAllowed, maxSecretLevel);
        return files.selectFiles(archiveId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArchiveFile upload(long archiveId, MultipartFile upload, String relativePath, String actor, long actorId,
        boolean admin, LongPredicate categoryAllowed, int maxSecretLevel) throws IOException, NoSuchAlgorithmException {
        return upload(archiveId, upload, relativePath, actor, actorId, admin, categoryAllowed, maxSecretLevel, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ArchiveFile upload(long archiveId, MultipartFile upload, String relativePath, String actor, long actorId,
        boolean admin, LongPredicate categoryAllowed, int maxSecretLevel, String fileName) throws IOException, NoSuchAlgorithmException {
        //上传单个附件并生成唯一存储名称
        //锁住档案行，避免上传期间档案被修改
        if (files.lockArchive(archiveId) == null) throw new ServiceException("档案不存在或无权访问");
        ArchiveRecord archive = requireArchive(archiveId, actor, admin, categoryAllowed, maxSecretLevel);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿可以上传附件");
        if (upload.isEmpty()) throw new ServiceException("请选择非空文件");
        String name = fileName == null || fileName.isBlank() ? upload.getOriginalFilename() : fileName;
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
        ArchiveFileVersion initial = new ArchiveFileVersion();
        initial.setFileId(file.getFileId());
        initial.setVersionNo(1);
        initial.setOriginalName(name);
        initial.setStoreKey(storeKey);
        initial.setFileType(type);
        initial.setFileSize(file.getFileSize());
        initial.setSha256(file.getSha256());
        initial.setCreateBy(actor);
        files.insertVersion(initial);
        file.setCurrentVersionNo(1);
        file.setRevoked(0);
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_UPLOAD", actorId, actor,
            fileDetail(file.getFileId(), "上传附件 V1：" + name));
        return file;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long archiveId, long fileId, String actor, long actorId, boolean admin,
        LongPredicate categoryAllowed, int maxSecretLevel) {
        // 兼容旧删除接口，统一逻辑撤销，保留版本和操作历史。
        revoke(archiveId, fileId, "旧删除入口撤销", actor, actorId, admin);
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

    @Transactional(rollbackFor = Exception.class)
    //传新版本
    public ArchiveFileVersion addVersion(long archiveId, long fileId, MultipartFile upload, String actor, long actorId, boolean admin) throws IOException, NoSuchAlgorithmException {
        files.lockArchive(archiveId);//debug：需要先锁定档案记录，否则会发生冲突
        ArchiveRecord archive = requireArchive(archiveId, actor, admin);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿可以新增版本");
        ArchiveFile current = files.selectFile(archiveId, fileId);
        if (current == null || Integer.valueOf(1).equals(current.getRevoked())) {
            throw new ServiceException("附件不存在或已撤销");
        }
        ArchiveFileVersion version = writeVersionFile(upload, current.getDisplayName(), actor);
        version.setFileId(fileId);
        version.setVersionNo(files.selectNextVersionNo(fileId));
        files.insertVersion(version);
        files.updateCurrentSnapshot(archiveId, version);
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_VERSION_ADD", actorId, actor,
                fileDetail(fileId, "新版本V" + version.getVersionNo() + "：" + version.getOriginalName()));
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    //回滚版本rollback
    public ArchiveFileVersion rollback(long archiveId, long fileId, int sourceVersionNo, String actor, long actorId, boolean admin) {
        files.lockArchive(archiveId);
        ArchiveRecord archive = requireArchive(archiveId, actor, admin);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿可以回退版本");
        ArchiveFile current = files.selectFile(archiveId, fileId);
        ArchiveFileVersion source = files.selectVersion(fileId, sourceVersionNo);
        if (current == null || Integer.valueOf(1).equals(current.getRevoked()) || source == null) {
            throw new ServiceException("附件或来源版本不存在");
        }
        if (Integer.valueOf(sourceVersionNo).equals(current.getCurrentVersionNo())) {
            throw new ServiceException("不能回退到当前版本");
        }
        ArchiveFileVersion next = copyVersion(source);
        next.setVersionNo(files.selectNextVersionNo(fileId));
        next.setSourceVersionNo(sourceVersionNo);//溯源：给出来源版本号
        next.setCreateBy(actor);
        next.setCreateTime(new Date());
        files.insertVersion(next);
        files.updateCurrentSnapshot(archiveId, next);
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_VERSION_ROLLBACK", actorId, actor,
                fileDetail(fileId, "回退到 V" + sourceVersionNo + "，生成 V" + next.getVersionNo()));
        return next;
    }

    @Transactional(rollbackFor = Exception.class)
    //撤销附件，但是逻辑撤销i
    public void revoke(long archiveId, long fileId, String reason, String actor, long actorId, boolean admin) {
        files.lockArchive(archiveId);
        ArchiveRecord archive = requireArchive(archiveId, actor, admin);
        if (!"DRAFT".equals(archive.getArchiveStatus())) throw new ServiceException("只有草稿附件可以撤销");
        if (files.revokeFile(archiveId, fileId) == 0) throw new ServiceException("附件不存在或已撤销");
        archives.insertAudit(archiveId, archive.getArchiveCode(), "FILE_REVOKE", actorId, actor,
                fileDetail(fileId, "撤销附件：" + reason));
    }

    //版本查询
    public List<ArchiveFileVersion> versions(long archiveId, long fileId, String actor, boolean admin) {
        requireArchive(archiveId, actor, admin);
        ArchiveFile file = files.selectFile(archiveId, fileId);
        if (file == null || Integer.valueOf(1).equals(file.getRevoked())) throw new ServiceException("附件不存在");
        return files.selectVersions(fileId);
    }

    public List<Map<String, Object>> usageStatistics(long archiveId, LocalDate dateFrom, LocalDate dateTo,
        String archiveStatus, String groupBy, String actor, boolean admin) {
        requireArchive(archiveId, actor, admin);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))
            throw new ServiceException("开始日期不能晚于结束日期");
        if (!"eventType".equals(groupBy) && !"fileType".equals(groupBy))
            throw new ServiceException("不支持的统计维度");
        return files.selectUsageStatistics(archiveId, dateFrom, dateTo, archiveStatus, groupBy);
    }

    //附件操作历史查询
    public List<Map<String, Object>> operations(long archiveId, long fileId, String actor, boolean admin) {
        requireArchive(archiveId, actor, admin);
        if (files.selectFile(archiveId, fileId) == null) throw new ServiceException("附件不存在");
        return files.selectFileOperations(archiveId, fileId);
    }

    //版本信息比较
    public Map<String, Object> compare(long archiveId, long fileId, int leftVersionNo, int rightVersionNo, String actor, boolean admin) {
        requireArchive(archiveId, actor, admin);
        if (files.selectFile(archiveId, fileId) == null) throw new ServiceException("附件不存在");
        ArchiveFileVersion left = files.selectVersion(fileId, leftVersionNo);
        ArchiveFileVersion right = files.selectVersion(fileId, rightVersionNo);
        if (left == null || right == null) throw new ServiceException("比较版本不存在");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("left", left);
        result.put("right", right);
        result.put("sameContent", left.getSha256().equalsIgnoreCase(right.getSha256()));
        result.put("sameName", left.getOriginalName().equals(right.getOriginalName()));
        result.put("sizeDelta", right.getFileSize() - left.getFileSize());
        result.put("typeChanged", !left.getFileType().equalsIgnoreCase(right.getFileType()));
        return result;
    }

    //record的预览下载打印，and sth else
    @Transactional(rollbackFor = Exception.class)
    public void recordUse(long archiveId, long fileId, int versionNo, String eventType, String actor, long actorId, boolean admin) {
        ArchiveRecord archive = requireArchive(archiveId, actor, admin);
        version(archiveId, fileId, versionNo, actor, admin);
        archives.insertAudit(archiveId, archive.getArchiveCode(), eventType, actorId, actor,
                fileDetail(fileId, eventType + " V" + versionNo));
    }

    //查询单个版本
    public ArchiveFileVersion version(long archiveId, long fileId, int versionNo, String actor, boolean admin) {
        requireArchive(archiveId, actor, admin);
        if (files.selectFile(archiveId, fileId) == null) throw new ServiceException("附件不存在");
        ArchiveFileVersion value = files.selectVersion(fileId, versionNo);
        if (value == null) throw new ServiceException("版本不存在");
        return value;
    }

    //定位文件在本地的path
    public Path resolveVersion(long archiveId, long fileId, int versionNo, String actor, boolean admin) {
        ArchiveFileVersion version = version(archiveId, fileId, versionNo, actor, admin);
        Path path = root.resolve(version.getStoreKey()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new ServiceException("附件文件不存在");
        return path;
    }

    //保存上传的文件
    private ArchiveFileVersion writeVersionFile(MultipartFile upload, String fallbackName, String actor)
            throws IOException, NoSuchAlgorithmException {
        if (upload == null || upload.isEmpty()) throw new ServiceException("请选择非空文件");
        String name = upload.getOriginalFilename();
        name = name == null || name.isBlank() ? fallbackName : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255) throw new ServiceException("文件名无效");//debug：拦截超长文件名
        String type = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) throw new ServiceException("不支持此类文件");
        String storeKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + type;
        Path target = root.resolve(storeKey).normalize();
        Files.createDirectories(target.getParent());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) FileSystemUtils.deleteRecursively(target.toFile());
            }
        });
        try (InputStream input = new DigestInputStream(upload.getInputStream(), digest)) {
            Files.copy(input, target);
        }
        ArchiveFileVersion value = new ArchiveFileVersion();
        value.setOriginalName(name); value.setStoreKey(storeKey); value.setFileType(type);
        value.setFileSize(upload.getSize()); value.setSha256(HexFormat.of().formatHex(digest.digest()));
        value.setCreateBy(actor); value.setCreateTime(new Date());
        return value;
    }

    private ArchiveFileVersion copyVersion(ArchiveFileVersion source) {
        ArchiveFileVersion value = new ArchiveFileVersion();
        value.setFileId(source.getFileId()); value.setOriginalName(source.getOriginalName());
        value.setStoreKey(source.getStoreKey()); value.setFileType(source.getFileType());
        value.setFileSize(source.getFileSize()); value.setSha256(source.getSha256());
        return value;
    }

    private String fileDetail(long fileId, String text) { return "[file:" + fileId + "] " + text; }
}
