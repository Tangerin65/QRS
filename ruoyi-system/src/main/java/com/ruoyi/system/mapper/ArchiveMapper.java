package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface ArchiveMapper {
    List<ArchiveRecord> selectArchiveList(ArchiveQuery query);
    ArchiveRecord selectArchiveById(@Param("archiveId") long archiveId, @Param("ownerName") String ownerName,
                                    @Param("admin") boolean admin);
    List<Map<String, Object>> selectAuditEvents(long archiveId);
    int insertArchive(ArchiveRecord record);
    int updateArchive(ArchiveRecord record);
    int submitDraft(@Param("archiveId") long archiveId, @Param("ownerName") String ownerName,
                    @Param("admin") boolean admin, @Param("version") int version,
                    @Param("updateBy") String updateBy);
    int batchEditDraft(@Param("archiveId") long archiveId, @Param("ownerName") String ownerName,
                       @Param("admin") boolean admin, @Param("version") int version,
                       @Param("secretLevel") Integer secretLevel, @Param("retentionCode") String retentionCode, @Param("retentionName") String retentionName,
                       @Param("updateBy") String updateBy);
    int softDeleteArchive(@Param("archiveId") long archiveId, @Param("ownerName") String ownerName,
                          @Param("admin") boolean admin, @Param("version") int version,
                          @Param("updateBy") String updateBy);
    int insertSequence(@Param("categoryId") long categoryId, @Param("formingYear") int formingYear);
    int selectNextSequence(@Param("categoryId") long categoryId, @Param("formingYear") int formingYear);
    int incrementSequence(@Param("categoryId") long categoryId, @Param("formingYear") int formingYear);
    Integer selectSequence(@Param("categoryId") long categoryId, @Param("formingYear") int formingYear);
    Long selectIdByCode(String archiveCode);
    int insertAudit(@Param("archiveId") Long archiveId, @Param("archiveCode") String archiveCode,
                    @Param("eventType") String eventType, @Param("actorId") long actorId,
                    @Param("actorName") String actorName, @Param("detail") String detail);
    List<Map<String, Object>> selectStatistics(ArchiveQuery query);
}
