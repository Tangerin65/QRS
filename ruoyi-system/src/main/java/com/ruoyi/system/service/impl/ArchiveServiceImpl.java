package com.ruoyi.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ArchiveBatchEdit;
import com.ruoyi.system.domain.ArchiveQuery;
import com.ruoyi.system.domain.ArchiveRecord;
import com.ruoyi.system.mapper.ArchiveMapper;
import com.ruoyi.system.service.ArchiveCatalogService;
import com.ruoyi.system.service.ArchiveCatalogService.Catalog;
import com.ruoyi.system.service.ArchiveCatalogService.Choice;
import com.ruoyi.system.service.ArchiveImportValidator.ImportLine;
import com.ruoyi.system.service.ArchiveImportValidator;
import com.ruoyi.system.service.ArchiveSecretLevels;
import com.ruoyi.system.service.IArchiveService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.common.core.domain.entity.SysDictData;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveServiceImpl implements IArchiveService {
    private static final int MAX_IMPORT_ROWS = 1000;
    private final ArchiveMapper mapper;
    private final ArchiveCatalogService catalog;
    private final ISysDictTypeService dictTypes;
    private final ArchiveSecretLevels secretLevels;

    public ArchiveServiceImpl(ArchiveMapper mapper, ArchiveCatalogService catalog, ISysDictTypeService dictTypes,
                              ArchiveSecretLevels secretLevels) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.dictTypes = dictTypes;
        this.secretLevels = secretLevels;
    }

    @Override
    public List<ArchiveRecord> selectArchiveList(ArchiveQuery query) {
        validateQuery(query);
        if (query.getAllowedCategoryIds() != null && query.getAllowedCategoryIds().isEmpty()) return List.of();
        int pageNum = query.getMaxRows() > 0 ? 1 : query.getPageNum();
        int pageSize = query.getMaxRows() > 0 ? query.getMaxRows() : query.getPageSize();
        PageHelper.startPage(pageNum, pageSize);
        List<ArchiveRecord> rows = mapper.selectArchiveList(query);
        Map<Integer, String> labels = secretLevels.options();
        rows.forEach(record -> record.setSecretName(ArchiveSecretLevels.label(record.getSecretLevel(), labels)));
        return rows;
    }

    @Override
    public ArchiveRecord selectArchiveDetail(long archiveId, String ownerName, long actorId, boolean admin,
                                             java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        ArchiveRecord record = requireVisible(archiveId, ownerName, admin);
        requireRecordAccess(record, allowedCategory, maxSecretLevel);
        record.setSecretName(displaySecretName(record.getSecretLevel()));
        record.setAuditEvents(mapper.selectAuditEvents(archiveId));
        mapper.insertAudit(archiveId, record.getArchiveCode(), "VIEW", actorId, ownerName, "查看档案详情");
        return record;
    }

    @Override
    public ArchiveRecord selectDraftForEdit(long archiveId, String ownerName, boolean admin,
                                             java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        ArchiveRecord record = requireVisible(archiveId, ownerName, admin);
        requireRecordAccess(record, allowedCategory, maxSecretLevel);
        record.setSecretName(displaySecretName(record.getSecretLevel()));
        if (!"DRAFT".equals(record.getArchiveStatus())) throw new ServiceException("仅草稿档案可以修改");
        return record;
    }

    @Override
    @Transactional
    public ArchiveRecord createDraft(ArchiveRecord record, String actorName, long actorId, long deptId,
                                     java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        validateRecord(record);
        if (record.getSecretLevel() > maxSecretLevel) throw new ServiceException("无权设置此密级");
        Catalog category = catalog.requireRegistrationCategory(record.getCategoryId(), allowedCategory);
        record.setCategoryCode(category.code());
        record.setRetentionName(resolveRetentionName(record.getRetentionCode()));
        record.setArchiveCode(nextCode(category, record.getFormingDate()));
        record.setArchiveStatus("DRAFT");
        record.setVersion(1);
        record.setDeptId(deptId);
        record.setCreateBy(actorName);
        record.setMetadataJson(normalizeMetadata(record.getMetadataJson()));
        try {
            mapper.insertArchive(record);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("档号冲突，请重新提交");
        }
        mapper.insertAudit(record.getArchiveId(), record.getArchiveCode(), "CREATE_DRAFT", actorId, actorName, "创建档案草稿");
        return record;
    }

    @Override
    @Transactional
    public ArchiveRecord updateDraft(ArchiveRecord record, String actorName, long actorId, boolean admin,
                                     java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        ArchiveRecord existing = requireVisible(record.getArchiveId(), actorName, admin);
        requireRecordAccess(existing, allowedCategory, maxSecretLevel);
        if (!"DRAFT".equals(existing.getArchiveStatus())) throw new ServiceException("仅草稿档案可以修改");
        validateRecord(record);
        Catalog category = catalog.requireRegistrationCategory(record.getCategoryId(), allowedCategory);
        if (record.getSecretLevel() > maxSecretLevel) throw new ServiceException("无权设置此密级");
        record.setCategoryCode(category.code());
        record.setRetentionName(resolveRetentionName(record.getRetentionCode()));
        record.setArchiveCode(existing.getArchiveCode());
        record.setDeptId(existing.getDeptId());
        record.setCreateBy(existing.getCreateBy());
        record.setArchiveStatus(existing.getArchiveStatus());
        record.setUpdateBy(actorName);
        record.setMetadataJson(normalizeMetadata(record.getMetadataJson()));
        if (mapper.updateArchive(record) == 0) throw new ServiceException("档案已被修改，刷新后重试");
        mapper.insertAudit(record.getArchiveId(), record.getArchiveCode(), "UPDATE_DRAFT", actorId, actorName, "修改档案草稿");
        return requireVisible(record.getArchiveId(), actorName, admin);
    }

    @Override
    @Transactional
    public void submitDraft(long archiveId, int version, String actorName, long actorId, boolean admin,
                            java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        ArchiveRecord record = requireVisible(archiveId, actorName, admin);
        requireRecordAccess(record, allowedCategory, maxSecretLevel);
        if (!"DRAFT".equals(record.getArchiveStatus())) throw new ServiceException("仅草稿档案可以提交");
        if (mapper.submitDraft(archiveId, actorName, admin, version, actorName) == 0)
            throw new ServiceException("档案已被修改，刷新后重试");
        mapper.insertAudit(archiveId, record.getArchiveCode(), "SUBMIT", actorId, actorName, "提交档案");
    }

    @Override
    @Transactional
    public void deleteDraft(long archiveId, int version, String actorName, long actorId, boolean admin,
                            java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        ArchiveRecord existing = requireVisible(archiveId, actorName, admin);
        requireRecordAccess(existing, allowedCategory, maxSecretLevel);
        if (!"DRAFT".equals(existing.getArchiveStatus())) throw new ServiceException("仅草稿档案可以撤销");
        if (mapper.softDeleteArchive(archiveId, actorName, admin, version, actorName) == 0)
            throw new ServiceException("档案已被修改，刷新后重试");
        mapper.insertAudit(archiveId, existing.getArchiveCode(), "CANCEL_DRAFT", actorId, actorName, "撤销档案草稿");
    }

    @Override
    public String previewCode(long categoryId, LocalDate formingDate, java.util.function.LongPredicate allowedCategory) {
        Catalog category = catalog.requireRegistrationCategory(categoryId, allowedCategory);
        int year = formingDate == null ? LocalDate.now().getYear() : formingDate.getYear();
        Integer next = mapper.selectSequence(categoryId, year);
        return String.format("%s-%d-%06d", category.code(), year, next == null ? 1 : next);
    }

    @Override
    public List<Map<String, Object>> statistics(ArchiveQuery query) {
        validateQuery(query);
        if (query.getAllowedCategoryIds() != null && query.getAllowedCategoryIds().isEmpty()) return List.of();
        return mapper.selectStatistics(query);
    }

    @Override
    @Transactional
    public int importDrafts(List<ArchiveRecord> records, String actorName, long actorId, long deptId,
                            java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        if (records.isEmpty() || records.size() > MAX_IMPORT_ROWS) throw new ServiceException("每次导入1至1000条档案");
        for (int index = 0; index < records.size(); index++) {
            try {
                ArchiveRecord record = records.get(index);
                record.setArchiveId(null);
                record.setArchiveCode(null);
                record.setCategoryCode(null);
                record.setRetentionName(null);
                record.setArchiveStatus(null);
                record.setVersion(null);
                record.setDeptId(null);
                record.setCreateBy(null);
                record.setMetadataJson("{}");
                createDraft(record, actorName, actorId, deptId, allowedCategory, maxSecretLevel);
            } catch (ServiceException e) {
                throw new ServiceException("第" + (index + 2) + "行：" + e.getMessage() + "，整批未导入");
            }
        }
        return records.size();
    }

    @Override
    @Transactional
    public int batchEdit(ArchiveBatchEdit edit, String actorName, long actorId, boolean admin,
                         java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        if (edit.getUpdates() == null || edit.getUpdates().isEmpty() || edit.getUpdates().size() > MAX_IMPORT_ROWS)
            throw new ServiceException("请选择1至1000条档案");
        if (edit.getSecretLevel() == null && edit.getRetentionCode() == null)
            throw new ServiceException("请至少填写一个批量修改项");
        if (edit.getSecretLevel() != null) {
            secretLevels.requireEnabled(edit.getSecretLevel());
            if (edit.getSecretLevel() > maxSecretLevel) throw new ServiceException("无权设置此密级");
        }
        if (edit.getRetentionCode() != null && edit.getRetentionCode().length() > 32)
            throw new ServiceException("保管期限编码最多32个字符");
        String retentionName = edit.getRetentionCode() == null ? null : resolveRetentionName(edit.getRetentionCode());
        java.util.Set<Long> ids = new java.util.HashSet<>();
        List<ArchiveRecord> records = new java.util.ArrayList<>();
        for (ArchiveBatchEdit.Item item : edit.getUpdates()) {
            if (item == null || item.getArchiveId() == null || item.getVersion() == null || !ids.add(item.getArchiveId()))
                throw new ServiceException("批量修改档案参数无效");
            ArchiveRecord record = requireVisible(item.getArchiveId(), actorName, admin);
            requireRecordAccess(record, allowedCategory, maxSecretLevel);
            if (!"DRAFT".equals(record.getArchiveStatus())) throw new ServiceException("仅草稿档案可以批量修改");
            records.add(record);
        }
        for (int index = 0; index < edit.getUpdates().size(); index++) {
            ArchiveBatchEdit.Item item = edit.getUpdates().get(index);
            ArchiveRecord record = records.get(index);
            if (mapper.batchEditDraft(item.getArchiveId(), actorName, admin, item.getVersion(),
                    edit.getSecretLevel(), edit.getRetentionCode(), retentionName, actorName) == 0)
                throw new ServiceException("档案已被修改或无权操作，整批未更新");
            mapper.insertAudit(item.getArchiveId(), record.getArchiveCode(), "BATCH_UPDATE_DRAFT", actorId, actorName, "批量修改档案草稿");
        }
        return edit.getUpdates().size();
    }

    @Override
    public List<ImportLine> validateImport(org.springframework.web.multipart.MultipartFile file,
                                           java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        List<ImportLine> rows = ArchiveImportValidator.read(file, secretLevels.options().keySet());
        List<Choice> choices = catalog.registrationChoices("", allowedCategory);
        List<ImportLine> checked = new java.util.ArrayList<>();
        for (ImportLine line : rows) {
            String error = line.error();
            ArchiveRecord record = line.record();
            if (error.isEmpty() && record.getSecretLevel() > maxSecretLevel) error = "无权导入此密级";
            if (error.isEmpty() && (record.getRetentionCode() == null || record.getRetentionCode().isBlank()))
                error = "保管期限编码必填";
            if (error.isEmpty()) {
                try {
                    record.setRetentionName(resolveRetentionName(record.getRetentionCode()));
                } catch (ServiceException e) {
                    error = e.getMessage();
                }
            }
            if (error.isEmpty()) {
                ArchiveCatalogService.Choice choice = choices.stream()
                    .filter(item -> item.code().equals(record.getCategoryCode())).findFirst().orElse(null);
                if (choice == null) error = "分类编码不存在、未启用或无建档权限";
                else record.setCategoryId(choice.id());
            }
            checked.add(new ImportLine(line.row(), record, error));
        }
        return checked;
    }

    private void requireRecordAccess(ArchiveRecord record, java.util.function.LongPredicate allowedCategory, int maxSecretLevel) {
        if (record.getCategoryId() == null || !allowedCategory.test(record.getCategoryId()))
            throw new ServiceException("档案不存在或无权访问");
        if (record.getSecretLevel() == null || record.getSecretLevel() > maxSecretLevel)
            throw new ServiceException("档案不存在或无权访问");
    }

    private ArchiveRecord requireVisible(long id, String ownerName, boolean admin) {
        ArchiveRecord record = mapper.selectArchiveById(id, ownerName, admin);
        if (record == null) throw new ServiceException("档案不存在或无权访问");
        return record;
    }

    private String nextCode(Catalog category, LocalDate formingDate) {
        int year = formingDate.getYear();
        mapper.insertSequence(category.id(), year);
        int sequence = mapper.selectNextSequence(category.id(), year);
        mapper.incrementSequence(category.id(), year);
        return String.format("%s-%d-%06d", category.code(), year, sequence);
    }

    private String displaySecretName(Integer secretLevel) {
        //查询与导出使用同一套密级字典名称
        return ArchiveSecretLevels.label(secretLevel, secretLevels.options());
    }

    private String resolveRetentionName(String retentionCode) {
        if (retentionCode == null || retentionCode.isBlank()) return "";
        List<SysDictData> options = dictTypes.selectDictDataByType("arc_retention_period");
        if (options == null) throw new ServiceException("请先配置保管期限字典");
        return options.stream()
            .filter(item -> "0".equals(item.getStatus()) && retentionCode.equals(item.getDictValue()))
            .map(SysDictData::getDictLabel)
            .findFirst()
            .orElseThrow(() -> new ServiceException("请选择有效的保管期限"));
    }

    private void validateRecord(ArchiveRecord record) {
        if (record.getTitle() == null || record.getTitle().isBlank() || record.getTitle().length() > 200)
            throw new ServiceException("题名必填且最多200个字符");
        if (record.getCategoryId() == null || record.getFormingDate() == null)
            throw new ServiceException("分类和形成日期必填");
        secretLevels.requireEnabled(record.getSecretLevel());
        if (record.getRetentionCode() == null || record.getRetentionCode().isBlank())
            throw new ServiceException("保管期限必选");
        if (record.getRetentionCode().length() > 32) throw new ServiceException("保管期限编码最多32个字符");
        if (record.getSummary() == null) record.setSummary("");
        if (record.getSummary().length() > 1000) throw new ServiceException("摘要最多1000个字符");
    }

    private String normalizeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return "{}";
        Object parsed = JSON.parse(metadata);
        if (!(parsed instanceof Map<?, ?>)) throw new ServiceException("扩展元数据须为JSON对象");
        return JSON.toJSONString(parsed);
    }

    private void validateQuery(ArchiveQuery query) {
        if (query.getDateFrom() != null && query.getDateTo() != null && query.getDateFrom().isAfter(query.getDateTo()))
            throw new ServiceException("起始日期不能晚于结束日期");
        if (query.getSecretLevel() != null && (query.getSecretLevel() < 0 || query.getSecretLevel() > Byte.MAX_VALUE))
            throw new ServiceException("密级编码须为0至127的整数");
        String sort = query.getSortBy();
        if (!List.of("", "archiveCode", "title", "formingDate", "createTime").contains(sort == null ? "" : sort))
            query.setSortBy("createTime");
        String requestedSort = query.getOrderByColumn();
        query.setSortBy(switch (requestedSort == null ? "" : requestedSort) {
            case "archiveCode", "archive_code" -> "archiveCode";
            case "title" -> "title";
            case "formingDate", "forming_date" -> "formingDate";
            default -> "createTime";
        });
        query.setSortOrder("asc".equalsIgnoreCase(query.getIsAsc()) ? "asc" : "desc");
        query.setPageNum(query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum());
        query.setPageSize(query.getPageSize() == null ? 20 : Math.max(1, Math.min(200, query.getPageSize())));
    }
}
