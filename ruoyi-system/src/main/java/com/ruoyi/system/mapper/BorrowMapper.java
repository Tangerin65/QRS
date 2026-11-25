package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.ApprovalRecord;
import com.ruoyi.system.domain.BorrowQuery;
import com.ruoyi.system.domain.BorrowRecord;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** 借阅与审批的数据访问。 */
public interface BorrowMapper {
    List<BorrowRecord> selectBorrowList(BorrowQuery query);

    BorrowRecord selectBorrowById(@Param("borrowId") long borrowId);

    int insertBorrow(BorrowRecord record);

    int countActiveByArchive(@Param("archiveId") long archiveId);

    int countByPrefix(@Param("prefix") String prefix);

    Map<String, Object> selectArchiveForBorrow(@Param("archiveId") long archiveId);

    List<Map<String, Object>> selectBorrowableArchives(@Param("keyword") String keyword, @Param("limit") int limit);

    int updateBorrowStatus(@Param("borrowId") long borrowId, @Param("from") String from, @Param("to") String to,
                           @Param("operator") String operator);

    int markApproved(@Param("borrowId") long borrowId, @Param("approverId") long approverId,
                     @Param("operator") String operator);

    int markLent(@Param("borrowId") long borrowId, @Param("lendBy") long lendBy, @Param("operator") String operator);

    int markReturned(@Param("borrowId") long borrowId, @Param("returnBy") long returnBy,
                     @Param("operator") String operator);

    int insertApproval(ApprovalRecord record);

    List<ApprovalRecord> selectApprovals(@Param("borrowId") long borrowId);

    Map<String, Object> selectBorrowStatistics(BorrowQuery query);

    Map<String, Object> selectArchiveByCode(@Param("archiveCode") String archiveCode);

    int insertImportedBorrow(BorrowRecord record);

    /* 续借 */
    int insertRenew(com.ruoyi.system.domain.BorrowRenew renew);

    com.ruoyi.system.domain.BorrowRenew selectRenewById(@Param("renewId") long renewId);

    int countPendingRenew(@Param("borrowId") long borrowId);

    int approveRenew(@Param("renewId") long renewId, @Param("approverId") long approverId,
                     @Param("approverName") String approverName, @Param("opinion") String opinion);

    int rejectRenew(@Param("renewId") long renewId, @Param("approverId") long approverId,
                    @Param("approverName") String approverName, @Param("opinion") String opinion);

    int applyRenewToBorrow(@Param("borrowId") long borrowId, @Param("newEndDate") java.time.LocalDate newEndDate,
                           @Param("operator") String operator);

    int closePendingRenew(@Param("borrowId") long borrowId, @Param("operator") String operator);

    List<com.ruoyi.system.domain.BorrowRenew> selectRenewsByBorrow(@Param("borrowId") long borrowId);

    List<com.ruoyi.system.domain.BorrowRenew> selectPendingRenews();

    int insertAudit(@Param("archiveId") Long archiveId, @Param("archiveCode") String archiveCode,
                    @Param("eventType") String eventType, @Param("actorId") long actorId,
                    @Param("actorName") String actorName, @Param("detail") String detail);

    List<Map<String, Object>> selectBorrowAudits(@Param("archiveId") Long archiveId);
}
