package com.ruoyi.system.service;

import com.ruoyi.system.domain.ArchiveBatchEdit;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

public interface IArchiveService {
    List<ArchiveRecord> selectArchiveList(ArchiveQuery query);
    ArchiveRecord selectArchiveDetail(long archiveId, String ownerName, long actorId, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
    ArchiveRecord selectDraftForEdit(long archiveId, String ownerName, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
    ArchiveRecord createDraft(ArchiveRecord record, String actorName, long actorId, long deptId, LongPredicate allowedCategory, int maxSecretLevel);
    void submitDraft(long archiveId, int version, String actorName, long actorId, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
    ArchiveRecord updateDraft(ArchiveRecord record, String actorName, long actorId, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
    void deleteDraft(long archiveId, int version, String actorName, long actorId, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
    String previewCode(long categoryId, java.time.LocalDate formingDate, LongPredicate allowedCategory);
    List<Map<String, Object>> statistics(ArchiveQuery query);
    List<ArchiveImportValidator.ImportLine> validateImport(org.springframework.web.multipart.MultipartFile file, LongPredicate allowedCategory, int maxSecretLevel);
    int importDrafts(List<ArchiveRecord> records, String actorName, long actorId, long deptId, LongPredicate allowedCategory, int maxSecretLevel);
    int batchEdit(ArchiveBatchEdit edit, String actorName, long actorId, boolean admin, LongPredicate allowedCategory, int maxSecretLevel);
}
