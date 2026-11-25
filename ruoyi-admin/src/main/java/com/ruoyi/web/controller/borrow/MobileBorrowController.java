package com.ruoyi.web.controller.borrow;

import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BorrowQuery;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.service.IArchiveBindingService;
import com.ruoyi.system.service.IBorrowService;
import com.ruoyi.system.service.ISysUserService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M07 借阅的小程序接口。
 *
 * 路径在 /api/mobile/** 下，Shiro 整段放行，鉴权由 Bearer 令牌完成（与 M12 约定一致）：
 * 令牌 → wx_token → userId，再走与 PC 相同的 IBorrowService，权限与状态规则完全复用。
 */
@RestController
@RequestMapping("/api/mobile/borrow")
public class MobileBorrowController {
    private final IArchiveBindingService binding;
    private final IBorrowService borrows;
    private final ISysUserService users;

    public MobileBorrowController(IArchiveBindingService binding, IBorrowService borrows, ISysUserService users) {
        this.binding = binding;
        this.borrows = borrows;
        this.users = users;
    }

    /** 我的借阅列表。body 可选：borrowStatus、pageNum、pageSize。 */
    @PostMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String auth,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        SysUser user = current(auth);
        if (user == null) return unauthorized();
        BorrowQuery query = new BorrowQuery();
        query.setUserId(user.getUserId());
        query.setCanApprove(false);
        if (body != null) {
            Object status = body.get("borrowStatus");
            if (status != null && !String.valueOf(status).isBlank()) query.setBorrowStatus(String.valueOf(status));
            query.setPageNum(intValue(body.get("pageNum"), 1));
            query.setPageSize(intValue(body.get("pageSize"), 20));
        }
        try {
            List<BorrowRecord> rows = borrows.selectBorrowList(query);
            PageInfo<BorrowRecord> info = new PageInfo<>(rows);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", rows);
            data.put("total", info.getTotal());
            return ok(data);
        } catch (ServiceException e) {
            return fail(e.getMessage());
        }
    }

    /** 可借档案（已提交 / 已归档）。 */
    @GetMapping("/archives")
    public ResponseEntity<Map<String, Object>> archives(@RequestHeader(value = "Authorization", required = false) String auth,
                                                        @RequestParam(required = false) String keyword) {
        if (current(auth) == null) return unauthorized();
        return ok(borrows.selectBorrowableArchives(keyword, 50));
    }

    /** 提交借阅申请。body：archiveId、reason、startDate、endDate（yyyy-MM-dd）。 */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(@RequestHeader(value = "Authorization", required = false) String auth,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        SysUser user = current(auth);
        if (user == null) return unauthorized();
        if (body == null) return fail("参数不能为空");
        try {
            BorrowRecord record = new BorrowRecord();
            record.setArchiveId(longValue(body.get("archiveId")));
            record.setReason(text(body.get("reason")));
            record.setStartDate(date(body.get("startDate")));
            record.setEndDate(date(body.get("endDate")));
            BorrowRecord saved = borrows.apply(record, user.getLoginName(), user.getUserId(), user.getDeptId());
            return ok(saved);
        } catch (ServiceException e) {
            return fail(e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return fail("日期格式应为 yyyy-MM-dd");
        }
    }

    /** 本人借阅统计：服务端固定当前用户，按状态计数并统计逾期。 */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics(@RequestHeader(value = "Authorization", required = false) String auth) {
        SysUser user = current(auth);
        if (user == null) return unauthorized();
        BorrowQuery query = new BorrowQuery();
        query.setUserId(user.getUserId());
        query.setCanApprove(false);
        return ok(borrows.statistics(query));
    }

    /** 借阅详情（本人或审批人）。 */
    @GetMapping("/detail/{borrowId}")
    public ResponseEntity<Map<String, Object>> detail(@RequestHeader(value = "Authorization", required = false) String auth,
                                                      @PathVariable long borrowId) {
        SysUser user = current(auth);
        if (user == null) return unauthorized();
        try {
            return ok(borrows.selectBorrowDetail(borrowId, user.getUserId(), false));
        } catch (ServiceException e) {
            return fail(e.getMessage());
        }
    }

    private SysUser current(String auth) {
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = auth.substring(7).strip();
        if (token.isEmpty()) return null;
        try {
            Map<String, Object> session = binding.session(token, "miniapp");
            Object userId = session == null ? null : session.get("userId");
            if (userId == null) return null;
            return users.selectUserById(((Number) userId).longValue());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("msg", "操作成功");
        if (data != null) body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 400);
        body.put("msg", message == null ? "操作失败" : message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "TOKEN_INVALID");
        body.put("msg", "登录状态已失效，请重新登录");
        return ResponseEntity.status(401).body(body);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDate date(Object value) {
        String text = text(value);
        if (text == null || text.isBlank()) return null;
        return LocalDate.parse(text.strip());
    }
}
