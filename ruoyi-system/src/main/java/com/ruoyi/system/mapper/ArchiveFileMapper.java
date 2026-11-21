package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.ArchiveFile;
import org.apache.ibatis.annotations.Param;

public interface ArchiveFileMapper {
    List<ArchiveFile> selectFiles(@Param("archiveId") long archiveId);
    Long lockArchive(@Param("archiveId") long archiveId);
    int insertFile(ArchiveFile file);
    int deleteFile(@Param("fileId") long fileId, @Param("archiveId") long archiveId);
}
