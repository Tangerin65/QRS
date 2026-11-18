package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ArchiveRecord extends BaseEntity {
    private Long archiveId;
    private String archiveCode;
    private String title;
    private LocalDate formingDate;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Integer secretLevel;
    private String secretName;
    private String retentionCode;
    private String retentionName;
    private String summary;
    private String metadataJson;
    private String archiveStatus;
    private Integer version;
    private Long deptId;
    private List<Map<String, Object>> auditEvents;

    public Long getArchiveId() { return archiveId; }
    public void setArchiveId(Long archiveId) { this.archiveId = archiveId; }
    public String getArchiveCode() { return archiveCode; }
    public void setArchiveCode(String archiveCode) { this.archiveCode = archiveCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getFormingDate() { return formingDate; }
    public void setFormingDate(LocalDate formingDate) { this.formingDate = formingDate; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getSecretLevel() { return secretLevel; }
    public void setSecretLevel(Integer secretLevel) { this.secretLevel = secretLevel; }
    public String getSecretName() { return secretName; }
    public void setSecretName(String secretName) { this.secretName = secretName; }
    public String getRetentionCode() { return retentionCode; }
    public void setRetentionCode(String retentionCode) { this.retentionCode = retentionCode; }
    public String getRetentionName() { return retentionName; }
    public void setRetentionName(String retentionName) { this.retentionName = retentionName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(String archiveStatus) { this.archiveStatus = archiveStatus; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public List<Map<String, Object>> getAuditEvents() { return auditEvents; }
    public void setAuditEvents(List<Map<String, Object>> auditEvents) { this.auditEvents = auditEvents; }
}
