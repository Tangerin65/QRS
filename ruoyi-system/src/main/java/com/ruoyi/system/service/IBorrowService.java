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
}
