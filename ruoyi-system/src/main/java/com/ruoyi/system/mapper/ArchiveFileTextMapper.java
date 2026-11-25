package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.ArchiveFile;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ArchiveFileTextMapper {
    boolean indexExists();

    List<ArchiveFile> pendingFiles();

    void saveText(@Param("file") ArchiveFile file, @Param("content") String content, @Param("retry") boolean retry);
}
