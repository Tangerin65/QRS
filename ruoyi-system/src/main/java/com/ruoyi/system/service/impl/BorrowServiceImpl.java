package com.ruoyi.system.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ApprovalRecord;
import com.ruoyi.system.domain.BorrowQuery;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.BorrowRenew;
import com.ruoyi.system.mapper.BorrowMapper;
import com.ruoyi.system.service.IBorrowService;
import com.ruoyi.system.service.ISysUserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowServiceImpl implements IBorrowService {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String LENT = "LENT";
    public static final String RETURNED = "RETURNED";

    private static final Map<String, String> LABELS = Map.of(
            PENDING, "待审批", APPROVED, "已批准", REJECTED, "已驳回",
            WITHDRAWN, "已撤回", LENT, "已借出", RETURNED, "已归还");

    private final BorrowMapper mapper;
    private final ISysUserService users;

    /** 排序字段白名单，避免前端传入任意列名。 */
    private static final Map<String, String> SORTS = Map.of(
            "borrowNo", "b.borrow_no", "archiveTitle", "a.title", "applicantName", "b.applicant_name",
            "startDate", "b.start_date", "endDate", "b.end_date", "borrowStatus", "b.borrow_status",
            "createTime", "b.create_time");

    public BorrowServiceImpl(BorrowMapper mapper, ISysUserService users) {
        this.mapper = mapper;
        this.users = users;
    }

    @Override
    public List<BorrowRecord> selectBorrowList(BorrowQuery query) {
        String orderBy = orderBy(query);
        if (query.getMaxRows() > 0) {
            PageHelper.startPage(1, query.getMaxRows(), orderBy);
        } else {
            if (query.getPageNum() == null || query.getPageNum() < 1) query.setPageNum(1);
            if (query.getPageSize() == null || query.getPageSize() < 1 || query.getPageSize() > 100) query.setPageSize(20);
            PageHelper.startPage(query.getPageNum(), query.getPageSize(), orderBy);
        }
        List<BorrowRecord> rows = mapper.selectBorrowList(query);
        rows.forEach(this::decorate);
        return rows;
    }

    @Override
    public Map<String, Object> statistics(BorrowQuery query) {
        return mapper.selectBorrowStatistics(query);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> importBorrows(List<List<String>> rows, String operator, long actorId) {
        List<Map<String, Object>> results = new ArrayList<>();
        int line = 1;
        for (List<String> cells : rows) {
            line++;
            Map<String, Object> result = new HashMap<>();
            result.put("row", line);
            try {
                if (cells.size() < 5) throw new ServiceException("列数不足，至少需要档号、借阅人、事由、借期开始、应归还日");
                String code = cells.get(0);
                String loginName = cells.get(1);
                String reason = cells.get(2);
                if (code.isBlank() || loginName.isBlank()) throw new ServiceException("档号和借阅人不能为空");
                Map<String, Object> archive = mapper.selectArchiveByCode(code);
                if (archive == null) throw new ServiceException("档号不存在：" + code);
                SysUser user = users.selectUserByLoginName(loginName);
                if (user == null) throw new ServiceException("借阅人账号不存在：" + loginName);
                LocalDate start = LocalDate.parse(cells.get(3), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate end = LocalDate.parse(cells.get(4), DateTimeFormatter.ISO_LOCAL_DATE);
                if (end.isBefore(start)) throw new ServiceException("应归还日不能早于借期开始");
                String status = cells.size() > 5 && !cells.get(5).isBlank() ? cells.get(5).trim().toUpperCase() : RETURNED;
                if (!RETURNED.equals(status) && !LENT.equals(status) && !APPROVED.equals(status)) {
                    throw new ServiceException("状态只能是 RETURNED/LENT/APPROVED");
                }
                BorrowRecord record = new BorrowRecord();
                record.setBorrowNo(nextBorrowNo());
                record.setArchiveId(((Number) archive.get("archive_id")).longValue());
                record.setArchiveCode(code);
                record.setArchiveTitle(String.valueOf(archive.get("title")));
                record.setApplicantId(user.getUserId());
                record.setApplicantName(user.getUserName() == null ? user.getLoginName() : user.getUserName());
                record.setApplicantDeptId(user.getDeptId());
                record.setReason(reason);
                record.setStartDate(start);
                record.setEndDate(end);
                record.setActualReturnDate(RETURNED.equals(status) ? end : null);
                record.setBorrowStatus(status);
                record.setApprovalLevel(1);
                record.setCreateBy(operator);
                record.setRemark("导入历史记录");
                mapper.insertImportedBorrow(record);
                audit(record, "BORROW_IMPORT", actorId, operator, "导入历史借阅记录 " + record.getBorrowNo());
                result.put("ok", true);
                result.put("message", "导入成功：" + record.getBorrowNo());
            } catch (ServiceException | java.time.format.DateTimeParseException e) {
                result.put("ok", false);
                result.put("message", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    @Override
    @Transactional
    public BorrowRenew applyRenew(long borrowId, LocalDate requestedEndDate, String reason, String loginName, long userId) {
        if (requestedEndDate == null) throw new ServiceException("请选择新的应归还日");
        if (reason == null || reason.isBlank()) throw new ServiceException("请填写续借原因");
        BorrowRecord record = requireBorrow(borrowId);
        if (!LENT.equals(record.getBorrowStatus())) throw new ServiceException("只有已借出的借阅单可以续借");
        if (record.getApplicantId() == null || record.getApplicantId() != userId) throw new ServiceException("只能续借本人的借阅单");
        if (record.getEndDate() != null && !requestedEndDate.isAfter(record.getEndDate())) {
            throw new ServiceException("新的应归还日必须晚于当前应归还日");
        }
        if (mapper.countPendingRenew(borrowId) > 0) throw new ServiceException("该借阅单已有待审批的续借申请");

        BorrowRenew renew = new BorrowRenew();
        renew.setBorrowId(borrowId);
        renew.setApplicantId(userId);
        renew.setApplicantName(loginName);
        renew.setOldEndDate(record.getEndDate());
        renew.setRequestedEndDate(requestedEndDate);
        renew.setReason(reason);
        renew.setCreateBy(loginName);
        mapper.insertRenew(renew);
        renew.setRenewStatus(PENDING);
        audit(record, "BORROW_RENEW", userId, loginName,
                "申请续借至 " + requestedEndDate + "（原 " + record.getEndDate() + "）");
        return renew;
    }

    @Override
    @Transactional
    public void approveRenew(long renewId, String result, String opinion, String loginName, long userId, String userName) {
        if (!APPROVED.equals(result) && !REJECTED.equals(result)) throw new ServiceException("审批结论不合法");
        if (REJECTED.equals(result) && (opinion == null || opinion.isBlank())) throw new ServiceException("驳回必须填写意见");
        BorrowRenew renew = mapper.selectRenewById(renewId);
        if (renew == null) throw new ServiceException("续借申请不存在");
        if (!PENDING.equals(renew.getRenewStatus())) throw new ServiceException("该续借申请已处理");
        if (renew.getApplicantId() != null && renew.getApplicantId() == userId) {
            throw new ServiceException("不能审批自己的续借申请");
        }
        BorrowRecord borrow = requireBorrow(renew.getBorrowId());
        if (!LENT.equals(borrow.getBorrowStatus())) throw new ServiceException("借阅单已归还，不能续借");

        int changed = APPROVED.equals(result)
                ? mapper.approveRenew(renewId, userId, userName, opinion)
                : mapper.rejectRenew(renewId, userId, userName, opinion);
        if (changed == 0) throw new ServiceException("续借申请状态已变化，请刷新后重试");
        if (APPROVED.equals(result)
                && mapper.applyRenewToBorrow(renew.getBorrowId(), renew.getRequestedEndDate(), loginName) == 0) {
            throw new ServiceException("借阅单状态已变化，续借未生效");
        }
        audit(borrow, APPROVED.equals(result) ? "BORROW_RENEW_APPROVE" : "BORROW_RENEW_REJECT", userId, userName,
                APPROVED.equals(result) ? "批准续借至 " + renew.getRequestedEndDate() : "驳回续借：" + opinion);
    }

    @Override
    public List<BorrowRenew> selectRenews(long borrowId) {
        return mapper.selectRenewsByBorrow(borrowId);
    }

    @Override
    public List<BorrowRenew> selectPendingRenews() {
        return mapper.selectPendingRenews();
    }

    @Override
    public List<Map<String, Object>> selectDepts() {
        return mapper.selectBorrowDepts();
    }

    /** 借阅关键动作写入 arc_audit_event，便于详情页追溯。 */
    private void audit(BorrowRecord record, String type, long actorId, String actorName, String detail) {
        if (record == null || record.getArchiveId() == null) return;
        mapper.insertAudit(record.getArchiveId(), record.getArchiveCode(), type, actorId, actorName, detail);
    }

    /** 排序白名单 + 默认按创建时间倒序。 */
    private String orderBy(BorrowQuery query) {
        String column = query.getOrderByColumn();
        if (column == null || column.isBlank()) column = query.getSortBy();
        String mapped = column == null ? null : SORTS.get(column);
        if (mapped == null) return "b.create_time desc";
        String direction = query.getIsAsc();
        if (direction == null || direction.isBlank()) direction = query.getSortOrder();
        boolean asc = "asc".equalsIgnoreCase(direction);
        return mapped + (asc ? " asc" : " desc");
    }

    @Override
    public BorrowRecord selectBorrowDetail(long borrowId, long userId, boolean canApprove) {
        BorrowRecord record = mapper.selectBorrowById(borrowId);
        if (record == null) throw new ServiceException("借阅单不存在");
        if (!canApprove && (record.getApplicantId() == null || record.getApplicantId() != userId)) {
            throw new ServiceException("无权查看该借阅单");
        }
        decorate(record);
        Map<String, Object> extra = new HashMap<>();
        extra.put("approvals", mapper.selectApprovals(borrowId));
        extra.put("renews", mapper.selectRenewsByBorrow(borrowId));
        extra.put("audits", mapper.selectBorrowAudits(record.getArchiveId()));
        record.setParams(extra);
        return record;
    }

    @Override
    @Transactional
    public BorrowRecord apply(BorrowRecord record, String loginName, long userId, Long deptId) {
        if (record.getArchiveId() == null) throw new ServiceException("请选择要借阅的档案");
        if (record.getReason() == null || record.getReason().isBlank()) throw new ServiceException("请填写借阅事由");
        if (record.getStartDate() == null || record.getEndDate() == null) throw new ServiceException("请填写借期起止日期");
        if (record.getEndDate().isBefore(record.getStartDate())) throw new ServiceException("归还日期不能早于借阅日期");
        if (record.getEndDate().isBefore(LocalDate.now())) throw new ServiceException("归还日期不能早于今天");

        Map<String, Object> archive = mapper.selectArchiveForBorrow(record.getArchiveId());
        if (archive == null) throw new ServiceException("档案不存在");
        if (!"0".equals(String.valueOf(archive.get("del_flag")))) throw new ServiceException("该档案已撤销，不能借阅");
        String status = String.valueOf(archive.get("archive_status"));
        // TODO 与 M10 对齐：归档功能上线后收紧为只允许 ARCHIVED
        if (!"SUBMITTED".equals(status) && !"ARCHIVED".equals(status)) {
            throw new ServiceException("只有已提交或已归档的档案可以借阅");
        }
        if (mapper.countActiveByArchive(record.getArchiveId()) > 0) {
            throw new ServiceException("该档案已有未结束的借阅单");
        }

        record.setBorrowNo(nextBorrowNo());
        record.setArchiveCode(String.valueOf(archive.get("archive_code")));
        record.setArchiveTitle(String.valueOf(archive.get("title")));
        record.setApplicantId(userId);
        record.setApplicantName(loginName);
        record.setApplicantDeptId(deptId);
        record.setBorrowStatus(PENDING);
        record.setApprovalLevel(1);
        record.setRenewCount(0);
        record.setCreateBy(loginName);
        mapper.insertBorrow(record);
        audit(record, "BORROW_APPLY", userId, loginName, "提交借阅申请 " + record.getBorrowNo());
        decorate(record);
        return record;
    }

    @Override
    @Transactional
    public void approve(long borrowId, String result, String opinion, String loginName, long userId, String userName) {
        if (!APPROVED.equals(result) && !REJECTED.equals(result)) throw new ServiceException("审批结论不合法");
        if (REJECTED.equals(result) && (opinion == null || opinion.isBlank())) throw new ServiceException("驳回必须填写意见");
        BorrowRecord record = requireBorrow(borrowId);
        if (!PENDING.equals(record.getBorrowStatus())) throw new ServiceException("当前状态不能审批");
        if (record.getApplicantId() != null && record.getApplicantId() == userId) {
            throw new ServiceException("不能审批自己的借阅申请");
        }

        int changed = APPROVED.equals(result)
                ? mapper.markApproved(borrowId, userId, loginName)
                : mapper.updateBorrowStatus(borrowId, PENDING, REJECTED, loginName);
        if (changed == 0) throw new ServiceException("借阅单状态已变化，请刷新后重试");

        ApprovalRecord approval = new ApprovalRecord();
        approval.setBorrowId(borrowId);
        approval.setApprovalLevel(record.getApprovalLevel() == null ? 1 : record.getApprovalLevel());
        approval.setApproverId(userId);
        approval.setApproverName(userName);
        approval.setResult(result);
        approval.setOpinion(opinion);
        approval.setHandledAt(LocalDateTime.now());
        mapper.insertApproval(approval);
        audit(record, APPROVED.equals(result) ? "BORROW_APPROVE" : "BORROW_REJECT", userId, userName,
                APPROVED.equals(result) ? "借阅审批通过 " + record.getBorrowNo() : "借阅驳回：" + opinion);
    }

    @Override
    @Transactional
    public void lend(long borrowId, String loginName, long userId) {
        BorrowRecord record = requireBorrow(borrowId);
        if (!APPROVED.equals(record.getBorrowStatus())) throw new ServiceException("只有已批准的借阅单可以登记借出");
        if (mapper.markLent(borrowId, userId, loginName) == 0) throw new ServiceException("借阅单状态已变化，请刷新后重试");
        audit(record, "BORROW_LEND", userId, loginName, "登记借出 " + record.getBorrowNo());
    }

    @Override
    @Transactional
    public void giveBack(long borrowId, String loginName, long userId) {
        BorrowRecord record = requireBorrow(borrowId);
        if (!LENT.equals(record.getBorrowStatus())) throw new ServiceException("只有已借出的借阅单可以登记归还");
        if (mapper.markReturned(borrowId, userId, loginName) == 0) throw new ServiceException("借阅单状态已变化，请刷新后重试");
        // 归还后关闭未结的续借申请，避免归还后还能被批准
        mapper.closePendingRenew(borrowId, loginName);
        audit(record, "BORROW_RETURN", userId, loginName, "登记归还 " + record.getBorrowNo());
    }

    @Override
    @Transactional
    public void withdraw(long borrowId, String loginName, long userId) {
        BorrowRecord record = requireBorrow(borrowId);
        if (record.getApplicantId() == null || record.getApplicantId() != userId) throw new ServiceException("只能撤回本人的申请");
        if (!PENDING.equals(record.getBorrowStatus())) throw new ServiceException("只有待审批的申请可以撤回");
        if (mapper.updateBorrowStatus(borrowId, PENDING, WITHDRAWN, loginName) == 0) {
            throw new ServiceException("借阅单状态已变化，请刷新后重试");
        }
        audit(record, "BORROW_WITHDRAW", userId, loginName, "撤回借阅申请 " + record.getBorrowNo());
    }

    @Override
    public List<Map<String, Object>> selectBorrowableArchives(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        return mapper.selectBorrowableArchives(keyword == null ? null : keyword.trim(), size);
    }

    private BorrowRecord requireBorrow(long borrowId) {
        BorrowRecord record = mapper.selectBorrowById(borrowId);
        if (record == null) throw new ServiceException("借阅单不存在");
        return record;
    }

    /** 展示字段：状态中文、逾期与剩余天数（未归还且已过应归还日算逾期）。 */
    private void decorate(BorrowRecord record) {
        record.setStatusLabel(LABELS.getOrDefault(record.getBorrowStatus(), record.getBorrowStatus()));
        if (LENT.equals(record.getBorrowStatus()) && record.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), record.getEndDate());
            record.setDaysLeft(days);
            record.setOverdue(days < 0);
        } else {
            record.setOverdue(false);
        }
    }

    /** 借阅单号：JY + 日期 + 4 位流水；唯一键兜底，冲突时重试。 */
    private String nextBorrowNo() {
        String prefix = "JY" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 5; attempt++) {
            int seq = mapper.countByPrefix(prefix) + 1 + attempt;
            String no = prefix + String.format("%04d", seq);
            if (mapper.countByPrefix(no) == 0) return no;
        }
        throw new ServiceException("借阅单号生成失败，请重试");
    }
}
