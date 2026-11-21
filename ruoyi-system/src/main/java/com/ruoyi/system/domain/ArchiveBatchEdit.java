package com.ruoyi.system.domain;

import java.util.List;

public class ArchiveBatchEdit {
    private List<Item> updates;
    private Integer secretLevel;
    private String retentionCode;

    public List<Item> getUpdates() { return updates; }
    public void setUpdates(List<Item> updates) { this.updates = updates; }
    public Integer getSecretLevel() { return secretLevel; }
    public void setSecretLevel(Integer secretLevel) { this.secretLevel = secretLevel; }
    public String getRetentionCode() { return retentionCode; }
    public void setRetentionCode(String retentionCode) { this.retentionCode = retentionCode; }

    public static class Item {
        private Long archiveId;
        private Integer version;
        public Long getArchiveId() { return archiveId; }
        public void setArchiveId(Long archiveId) { this.archiveId = archiveId; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }
}
