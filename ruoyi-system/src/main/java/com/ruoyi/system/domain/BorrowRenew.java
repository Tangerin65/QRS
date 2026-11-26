package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 借阅续借申请，对应 arc_borrow_renew；borrowNo/archiveTitle 为展示字段。 */
public class BorrowRenew extends BaseEntity {
    private Long renewId;
    private Long borrowId;
    private String borrowNo;
    private String archiveTitle;
    private Long applicantId;
    private String applicantName;
    private LocalDate oldEndDate;
    private LocalDate requestedEndDate;
    private String reason;
    private String renewStatus;
    private Long approverId;
    private String approverName;
    private String opinion;
    private LocalDateTime handledAt;
    private Integer version;

    public Long getRenewId() { return renewId; }
    public void setRenewId(Long renewId) { this.renewId = renewId; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public String getBorrowNo() { return borrowNo; }
    public void setBorrowNo(String borrowNo) { this.borrowNo = borrowNo; }
    public String getArchiveTitle() { return archiveTitle; }
    public void setArchiveTitle(String archiveTitle) { this.archiveTitle = archiveTitle; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public LocalDate getOldEndDate() { return oldEndDate; }
    public void setOldEndDate(LocalDate oldEndDate) { this.oldEndDate = oldEndDate; }
    public LocalDate getRequestedEndDate() { return requestedEndDate; }
    public void setRequestedEndDate(LocalDate requestedEndDate) { this.requestedEndDate = requestedEndDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRenewStatus() { return renewStatus; }
    public void setRenewStatus(String renewStatus) { this.renewStatus = renewStatus; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
