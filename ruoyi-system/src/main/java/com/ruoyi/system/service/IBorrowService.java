package com.ruoyi.system.service;

import com.ruoyi.system.domain.BorrowQuery;
import com.ruoyi.system.domain.BorrowRecord;
import java.util.List;
import java.util.Map;

/** M07 借阅与审批业务。 */
public interface IBorrowService {
    List<BorrowRecord> selectBorrowList(BorrowQuery query);

    BorrowRecord selectBorrowDetail(long borrowId, long userId, boolean canApprove);

    BorrowRecord apply(BorrowRecord record, String loginName, long userId, Long deptId);

    void approve(long borrowId, String result, String opinion, String loginName, long userId, String userName);

    void lend(long borrowId, String loginName, long userId);

    void giveBack(long borrowId, String loginName, long userId);

    void withdraw(long borrowId, String loginName, long userId);

    List<Map<String, Object>> selectBorrowableArchives(String keyword, int limit);

    /** 当前筛选条件下的状态统计与逾期数量。 */
    Map<String, Object> statistics(BorrowQuery query);

    /** 导入历史借阅记录，逐行返回 row/ok/message。 */
    List<Map<String, Object>> importBorrows(List<List<String>> rows, String operator, long actorId);

    /** 提交续借申请。 */
    com.ruoyi.system.domain.BorrowRenew applyRenew(long borrowId, java.time.LocalDate requestedEndDate, String reason,
                                                   String loginName, long userId);

    /** 借阅单里出现过的部门，用于统计筛选。 */
    List<Map<String, Object>> selectDepts();

    /** 审批续借：APPROVED 时同步延长原借阅单的应归还日。 */
    void approveRenew(long renewId, String result, String opinion, String loginName, long userId, String userName);

    List<com.ruoyi.system.domain.BorrowRenew> selectRenews(long borrowId);

    List<com.ruoyi.system.domain.BorrowRenew> selectPendingRenews();

    /**
     * 借阅授权：供权限模块（M13）判断某用户对某档案当前是否可查看原文。
     * 返回 allowed、actions、expireAt、borrowNo、status；无有效借阅时 allowed=false。
     */
    Map<String, Object> authorization(long archiveId, long userId);
}
