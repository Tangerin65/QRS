package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.util.List;

public class ArchiveQuery extends BaseEntity {
    private String archiveCode;
    private String title;
    private String keyword;
    private Long categoryId;
    private Integer secretLevel;
    private String archiveStatus;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String ownerName;
    private boolean admin;
    private List<Long> allowedCategoryIds;
    private int maxSecretLevel;
    private int maxRows;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String orderByColumn;
    private String isAsc;
    private String sortBy;
    private String sortOrder;

    public String getArchiveCode() { return archiveCode; }
    public void setArchiveCode(String archiveCode) { this.archiveCode = archiveCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword == null ? null : keyword.strip(); }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getSecretLevel() { return secretLevel; }
    public void setSecretLevel(Integer secretLevel) { this.secretLevel = secretLevel; }
    public String getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(String archiveStatus) { this.archiveStatus = archiveStatus; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public List<Long> getAllowedCategoryIds() { return allowedCategoryIds; }
    public void setAllowedCategoryIds(List<Long> allowedCategoryIds) { this.allowedCategoryIds = allowedCategoryIds; }
    public int getMaxSecretLevel() { return maxSecretLevel; }
    public void setMaxSecretLevel(int maxSecretLevel) { this.maxSecretLevel = maxSecretLevel; }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getOrderByColumn() { return orderByColumn; }
    public void setOrderByColumn(String orderByColumn) { this.orderByColumn = orderByColumn; }
    public String getIsAsc() { return isAsc; }
    public void setIsAsc(String isAsc) { this.isAsc = isAsc; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
}
