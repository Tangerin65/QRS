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
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/statistics")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult statistics(BorrowQuery query) {
        query.setUserId(getUserId());
        query.setCanApprove(canApprove());
        return AjaxResult.success(borrows.statistics(query));
    }

    @PostMapping("/renew")
    @ResponseBody
    @RequiresPermissions("borrowApply")
    @Log(title = "借阅续借", businessType = BusinessType.INSERT)
    public AjaxResult renew(long borrowId, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedEndDate,
                            String reason) {
        return AjaxResult.success(borrows.applyRenew(borrowId, requestedEndDate, reason, getLoginName(), getUserId()));
    }

    @PostMapping("/renew/approve")
    @ResponseBody
    @RequiresPermissions("borrowApprove")
    @Log(title = "续借审批", businessType = BusinessType.UPDATE)
    public AjaxResult approveRenew(long renewId, String result, String opinion) {
        var user = getSysUser();
        String name = user.getUserName() == null || user.getUserName().isBlank() ? getLoginName() : user.getUserName();
        borrows.approveRenew(renewId, result, opinion, getLoginName(), getUserId(), name);
        return success();
    }

    @GetMapping("/renew/pending")
    @ResponseBody
    @RequiresPermissions("borrowApprove")
    public AjaxResult pendingRenews() {
        return AjaxResult.success(borrows.selectPendingRenews());
    }

    @GetMapping("/depts")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult depts() {
        return AjaxResult.success(borrows.selectDepts());
    }

    /** 供 M13 权限模块调用的借阅授权查询：某档案当前是否对被查询用户开放查看。 */
    @GetMapping("/authorization")
    @ResponseBody
    @RequiresPermissions(QUERY)
    public AjaxResult authorization(long archiveId, @RequestParam(required = false) Long userId) {
        long target = userId == null ? getUserId() : userId;
        return AjaxResult.success(borrows.authorization(archiveId, target));
    }

    @GetMapping("/dashboard")
    @RequiresPermissions(QUERY)
    public String dashboard(Model model) {
        model.addAttribute("operator", getLoginName());
        return "borrow/dashboard";
    }

    @GetMapping("/export")
    @RequiresPermissions("borrowExport")
    @Log(title = "借阅导出", businessType = BusinessType.EXPORT)
    public void export(BorrowQuery query, HttpServletResponse response) throws IOException {
        query.setUserId(getUserId());
        query.setCanApprove(canApprove());
        query.setMaxRows(10000);
        List<BorrowRecord> rows = borrows.selectBorrowList(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=borrows.xlsx");
        BorrowWorkbook.writeRecords(response.getOutputStream(), rows);
    }

    @GetMapping("/print")
    @RequiresPermissions(QUERY)
    public String print(BorrowQuery query, Model model) {
        query.setUserId(getUserId());
        query.setCanApprove(canApprove());
        query.setMaxRows(500);
        model.addAttribute("rows", borrows.selectBorrowList(query));
        model.addAttribute("operator", getLoginName());
        model.addAttribute("printedAt", java.time.LocalDateTime.now().withNano(0).toString().replace("T", " "));
        model.addAttribute("criteria", criteria(query));
        return "borrow/print";
    }

    @GetMapping("/import/template")
    @RequiresPermissions("borrowImport")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=borrow-import-template.xlsx");
        BorrowWorkbook.writeTemplate(response.getOutputStream());
    }

    @PostMapping("/import")
    @ResponseBody
    @RequiresPermissions("borrowImport")
    @Log(title = "借阅导入", businessType = BusinessType.IMPORT)
    public AjaxResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return error("请选择要导入的 Excel 文件");
        return AjaxResult.success(borrows.importBorrows(BorrowWorkbook.readRows(file.getInputStream()), getLoginName(), getUserId()));
    }

    /** 打印页眉上的筛选条件说明。 */
    private String criteria(BorrowQuery query) {
        StringBuilder text = new StringBuilder();
        if (query.getBorrowStatus() != null && !query.getBorrowStatus().isBlank()) text.append("状态=").append(query.getBorrowStatus()).append("；");
        if (query.getArchiveTitle() != null && !query.getArchiveTitle().isBlank()) text.append("题名含").append(query.getArchiveTitle()).append("；");
        if (query.getApplicantName() != null && !query.getApplicantName().isBlank()) text.append("申请人含").append(query.getApplicantName()).append("；");
        if (query.getDateFrom() != null) text.append("应归还日≥").append(query.getDateFrom()).append("；");
        if (query.getDateTo() != null) text.append("应归还日≤").append(query.getDateTo()).append("；");
        return text.length() == 0 ? "全部记录" : text.toString();
    }
}
