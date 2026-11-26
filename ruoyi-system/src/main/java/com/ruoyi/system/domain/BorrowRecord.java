package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 借阅单。对应 arc_borrow；archiveCode/archiveTitle/statusLabel/overdue/daysLeft 为展示字段，不落库。
 */
public class BorrowRecord extends BaseEntity {
    private Long borrowId;
    private String borrowNo;
    private Long archiveId;
    private String archiveCode;
    private String archiveTitle;
    private Long applicantId;
    private String applicantName;
    private Long applicantDeptId;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualReturnDate;
    private String borrowStatus;
    private Integer approvalLevel;
    private Long currentApproverId;
    private Long lendBy;
    private LocalDateTime lendAt;
    private Long returnBy;
    private LocalDateTime returnAt;
    private Integer renewCount;
    private Integer version;

    /* 展示字段 */
    private String statusLabel;
    private Boolean overdue;
    private Long daysLeft;

    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public String getBorrowNo() { return borrowNo; }
    public void setBorrowNo(String borrowNo) { this.borrowNo = borrowNo; }
    public Long getArchiveId() { return archiveId; }
    public void setArchiveId(Long archiveId) { this.archiveId = archiveId; }
    public String getArchiveCode() { return archiveCode; }
    public void setArchiveCode(String archiveCode) { this.archiveCode = archiveCode; }
    public String getArchiveTitle() { return archiveTitle; }
    public void setArchiveTitle(String archiveTitle) { this.archiveTitle = archiveTitle; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(Long applicantDeptId) { this.applicantDeptId = applicantDeptId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDate getActualReturnDate() { return actualReturnDate; }
    public void setActualReturnDate(LocalDate actualReturnDate) { this.actualReturnDate = actualReturnDate; }
    public String getBorrowStatus() { return borrowStatus; }
    public void setBorrowStatus(String borrowStatus) { this.borrowStatus = borrowStatus; }
    public Integer getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(Integer approvalLevel) { this.approvalLevel = approvalLevel; }
    public Long getCurrentApproverId() { return currentApproverId; }
    public void setCurrentApproverId(Long currentApproverId) { this.currentApproverId = currentApproverId; }
    public Long getLendBy() { return lendBy; }
    public void setLendBy(Long lendBy) { this.lendBy = lendBy; }
    public LocalDateTime getLendAt() { return lendAt; }
    public void setLendAt(LocalDateTime lendAt) { this.lendAt = lendAt; }
    public Long getReturnBy() { return returnBy; }
    public void setReturnBy(Long returnBy) { this.returnBy = returnBy; }
    public LocalDateTime getReturnAt() { return returnAt; }
    public void setReturnAt(LocalDateTime returnAt) { this.returnAt = returnAt; }
    public Integer getRenewCount() { return renewCount; }
    public void setRenewCount(Integer renewCount) { this.renewCount = renewCount; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }
    public Long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(Long daysLeft) { this.daysLeft = daysLeft; }
}
