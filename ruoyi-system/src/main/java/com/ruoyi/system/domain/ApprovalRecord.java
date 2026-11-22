package com.ruoyi.system.domain;

import java.time.LocalDateTime;

/**
 * 借阅审批流水，对应 arc_approval_record。result 取 APPROVED 或 REJECTED。
 */
public class ApprovalRecord {
    private Long recordId;
    private Long borrowId;
    private Integer approvalLevel;
    private Long approverId;
    private String approverName;
    private String result;
    private String opinion;
    private LocalDateTime handledAt;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getBorrowId() { return borrowId; }
    public void setBorrowId(Long borrowId) { this.borrowId = borrowId; }
    public Integer getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(Integer approvalLevel) { this.approvalLevel = approvalLevel; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
