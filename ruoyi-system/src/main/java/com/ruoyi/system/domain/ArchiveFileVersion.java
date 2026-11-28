package com.ruoyi.system.domain;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;

//getters&setters for档案版本管理功能模块，M02
public class ArchiveFileVersion {
    private Long fileId;
    private Integer versionNo;
    private String originalName;
    private String storeKey;
    private String fileType;
    private Long fileSize;
    private String sha256;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private Integer sourceVersionNo;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    @JsonIgnore
    public String getStoreKey() { return storeKey; }
    public void setStoreKey(String storeKey) { this.storeKey = storeKey; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Integer getSourceVersionNo() { return sourceVersionNo; }
    public void setSourceVersionNo(Integer sourceVersionNo) { this.sourceVersionNo = sourceVersionNo; }
}
