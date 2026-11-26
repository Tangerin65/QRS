package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.time.LocalDate;

/**
 * 借阅列表查询条件。canApprove 由 Controller 按权限注入：有审批权限的人可看全部，否则只看本人。
 */
public class BorrowQuery extends BaseEntity {
    private String borrowNo;
    private String archiveTitle;
    private String applicantName;
    private String borrowStatus;
    private Long deptId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long userId;
    private boolean canApprove;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private int maxRows;
    private String sortBy;
    private String sortOrder;
    private String orderByColumn;
    private String isAsc;

    public String getBorrowNo() { return borrowNo; }
    public void setBorrowNo(String borrowNo) { this.borrowNo = borrowNo; }
    public String getArchiveTitle() { return archiveTitle; }
    public void setArchiveTitle(String archiveTitle) { this.archiveTitle = archiveTitle; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getBorrowStatus() { return borrowStatus; }
    public void setBorrowStatus(String borrowStatus) { this.borrowStatus = borrowStatus; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isCanApprove() { return canApprove; }
    public void setCanApprove(boolean canApprove) { this.canApprove = canApprove; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
    public String getOrderByColumn() { return orderByColumn; }
    public void setOrderByColumn(String orderByColumn) { this.orderByColumn = orderByColumn; }
    public String getIsAsc() { return isAsc; }
    public void setIsAsc(String isAsc) { this.isAsc = isAsc; }
}
