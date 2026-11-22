package com.ruoyi.web.controller.borrow;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.domain.BorrowQuery;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.service.IBorrowService;
import java.util.List;
import java.util.Map;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** M07 借阅与审批的页面与接口入口。 */
@Controller
@RequestMapping("/borrow")
public class BorrowController extends BaseController {
    private static final String QUERY = "borrowQuery";

    private final IBorrowService borrows;

    public BorrowController(IBorrowService borrows) {
        this.borrows = borrows;
    }

    /** 有审批权限或管理员的用户可以看到全组借阅单；其他人只看本人。 */
    private boolean canApprove() {
        return ShiroUtils.isAdmin(getUserId()) || SecurityUtils.getSubject().isPermitted("borrowApprove");
    }

    @GetMapping("/list")
    @RequiresPermissions(QUERY)
    public String page(Model model) {
        var subject = SecurityUtils.getSubject();
        model.addAttribute("canApply", subject.isPermitted("borrowApply"));
        model.addAttribute("canApprove", subject.isPermitted("borrowApprove"));
        model.addAttribute("canLend", subject.isPermitted("borrowLend"));
        model.addAttribute("canReturn", subject.isPermitted("borrowReturn"));
        return "borrow/list";
    }

    @PostMapping("/list")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public TableDataInfo list(BorrowQuery query) {
        query.setUserId(getUserId());
        query.setCanApprove(canApprove());
        return getDataTable(borrows.selectBorrowList(query));
    }

    @GetMapping("/detail/{borrowId}")
    @ResponseBody
    @RequiresPermissions("borrowView")
    public AjaxResult detail(@PathVariable long borrowId) {
        return AjaxResult.success(borrows.selectBorrowDetail(borrowId, getUserId(), canApprove()));
    }

    @PostMapping("/apply")
    @ResponseBody
    @RequiresPermissions("borrowApply")
    @Log(title = "借阅申请", businessType = BusinessType.INSERT)
    public AjaxResult apply(@RequestBody BorrowRecord record) {
        var user = getSysUser();
        return AjaxResult.success(borrows.apply(record, getLoginName(), getUserId(), user.getDeptId()));
    }

    @PostMapping("/approve")
    @ResponseBody
    @RequiresPermissions("borrowApprove")
    @Log(title = "借阅审批", businessType = BusinessType.UPDATE)
    public AjaxResult approve(long borrowId, String result, String opinion) {
        var user = getSysUser();
        String name = user.getUserName() == null || user.getUserName().isBlank() ? getLoginName() : user.getUserName();
        borrows.approve(borrowId, result, opinion, getLoginName(), getUserId(), name);
        return success();
    }

    @PostMapping("/lend")
    @ResponseBody
    @RequiresPermissions("borrowLend")
    @Log(title = "借阅借出", businessType = BusinessType.UPDATE)
    public AjaxResult lend(long borrowId) {
        borrows.lend(borrowId, getLoginName(), getUserId());
        return success();
    }

    @PostMapping("/return")
    @ResponseBody
    @RequiresPermissions("borrowReturn")
    @Log(title = "借阅归还", businessType = BusinessType.UPDATE)
    public AjaxResult giveBack(long borrowId) {
        borrows.giveBack(borrowId, getLoginName(), getUserId());
        return success();
    }

    @PostMapping("/withdraw")
    @ResponseBody
    @RequiresPermissions("borrowApply")
    @Log(title = "借阅撤回", businessType = BusinessType.UPDATE)
    public AjaxResult withdraw(long borrowId) {
        borrows.withdraw(borrowId, getLoginName(), getUserId());
        return success();
    }

    @GetMapping("/archives")
    @ResponseBody
    @RequiresPermissions("borrowApply")
    public AjaxResult archives(@RequestParam(required = false) String keyword) {
        List<Map<String, Object>> rows = borrows.selectBorrowableArchives(keyword, 50);
        return AjaxResult.success(rows);
    }
}
