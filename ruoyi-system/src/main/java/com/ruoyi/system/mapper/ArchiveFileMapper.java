package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.ArchiveFile;
import com.ruoyi.system.domain.ArchiveFileVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface ArchiveFileMapper {
    List<ArchiveFile> selectFiles(@Param("archiveId") long archiveId);
    ArchiveFile selectFile(@Param("archiveId") long archiveId, @Param("fileId") long fileId);
    Long lockArchive(@Param("archiveId") long archiveId);
    int insertFile(ArchiveFile file);
    int deleteFile(@Param("fileId") long fileId, @Param("archiveId") long archiveId);

    List<ArchiveFileVersion> selectVersions(@Param("fileId") long fileId);
    ArchiveFileVersion selectVersion(@Param("fileId") long fileId, @Param("versionNo") int versionNo);
    int selectNextVersionNo(@Param("fileId") long fileId);
    int insertVersion(@Param("version") ArchiveFileVersion version);
    int updateCurrentSnapshot(@Param("archiveId") long archiveId,
                              @Param("version") ArchiveFileVersion version);
    int revokeFile(@Param("archiveId") long archiveId, @Param("fileId") long fileId);
    List<Map<String, Object>> selectUsageStatistics(@Param("archiveId") long archiveId,
        @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo,
        @Param("archiveStatus") String archiveStatus, @Param("groupBy") String groupBy);
    List<Map<String, Object>> selectFileOperations(@Param("archiveId") long archiveId,
                                                   @Param("fileId") long fileId);
}