package com.ruoyi.web.controller.archive;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.IArchiveAccountService;
import com.ruoyi.system.service.IArchiveBindingService;
import com.ruoyi.system.service.ISysRoleService;

/**
 * M12-A 账号与组织底座 控制器。
 *
 * 接口与模块拆分文档第 2 节一一对应：
 *   GET    /archive/account                 页面
 *   GET    /archive/account/list            组合查询清单
 *   GET    /archive/account/{userId}        详情
 *   POST   /archive/account                 新增
 *   PUT    /archive/account                 修改
 *   PUT    /archive/account/status          停用/启用
 *   POST   /archive/account/import          Excel 导入
 *   GET    /archive/account/export          导出（与清单同条件）
 *   GET    /archive/account/template        下载导入模板
 *   GET    /archive/account/statistics      账号统计（图表与明细同源）
 *   GET    /archive/account/bindqrcode      绑定二维码（内容只为用户编号）
 *   GET    /archive/account/deptTree        部门树
 *   GET    /archive/account/roles           角色下拉
 *   POST   /archive/account/unbind/{userId} 管理员解绑微信（联动模块二）
 *
 * 权限点：arc:account:view 查看/导出/统计，arc:account:manage 新增/修改/停用/导入/二维码/解绑。
 *
 * @author member6
 */
@Controller
@RequestMapping("/archive/account")
public class ArchiveAccountController extends BaseController
{
    private static final String VIEW = "arc:account:view";

    private static final String MANAGE = "arc:account:manage";

    /** 账号详情里 OpenID 只回掩码，长度上限由绑定表决定 */
    private final IArchiveAccountService accountService;

    private final IArchiveBindingService bindingService;

    private final ISysRoleService roleService;

    public ArchiveAccountController(IArchiveAccountService accountService, IArchiveBindingService bindingService,
            ISysRoleService roleService)
    {
        this.accountService = accountService;
        this.bindingService = bindingService;
        this.roleService = roleService;
    }

    /**
     * 页面入口。CSRF 令牌与 M03 页面同一套机制，保证页面内写请求能通过校验。
     */
    @GetMapping
    @RequiresPermissions(VIEW)
    public String page(Model model)
    {
        var subject = SecurityUtils.getSubject();
        var session = subject.getSession();
        if (session.getAttribute(ShiroConstants.CSRF_TOKEN) == null)
        {
            session.setAttribute(ShiroConstants.CSRF_TOKEN, ServletUtils.generateToken());
        }
        model.addAttribute("canManage", subject.isPermitted(MANAGE));
        return "archive/account";
    }

    /**
     * 组合查询清单。
     *
     * 支持：姓名(userName)、部门(deptId，含下级)、角色(roleId)、状态(status)、
     * 用户编号(userNo)、档案员(arcArchivist)、登录账号(loginName)，任意两项即可组合。
     */
    @GetMapping("/list")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public TableDataInfo list(SysUser query)
    {
        startPage();
        List<SysUser> rows = accountService.selectAccountList(query);
        return getDataTable(rows);
    }

    /**
     * 不分页的完整结果，供导出、打印与统计使用，保证与页面同一查询条件。
     */
    @GetMapping("/all")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult all(SysUser query)
    {
        return AjaxResult.success(accountService.selectAccountList(query));
    }

    @GetMapping("/{userId}")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult detail(@PathVariable Long userId)
    {
        return AjaxResult.success(accountService.selectAccountDetail(userId));
    }

    @PostMapping
    @ResponseBody
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.INSERT)
    public AjaxResult add(@RequestBody SysUser input)
    {
        Long userId = accountService.saveAccount(input, getLoginName());
        return AjaxResult.success("账号已新增", userId);
    }

    @PutMapping
    @ResponseBody
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@RequestBody SysUser input)
    {
        accountService.saveAccount(input, getLoginName());
        return AjaxResult.success("账号已更新");
    }

    /**
     * 停用/启用。停用即等同「删除」，同时撤销该账号小程序令牌。
     */
    @PutMapping("/status")
    @ResponseBody
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.UPDATE)
    public AjaxResult changeStatus(@RequestBody SysUser input)
    {
        accountService.changeStatus(input.getUserId(), input.getStatus(), getLoginName());
        return AjaxResult.success("1".equals(input.getStatus()) ? "账号已停用" : "账号已启用");
    }

    /**
     * 账号统计：总数、启用/停用、档案员、已绑定，以及按部门、按角色分布。
     */
    @GetMapping("/statistics")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult statistics(SysUser query)
    {
        return AjaxResult.success(accountService.selectAccountStatistics(query));
    }

    /**
     * 导出当前查询结果（与页面同一条件、同一份数据）。
     */
    @GetMapping("/export")
    @RequiresPermissions(VIEW)
    @Log(title = "档案账号管理", businessType = BusinessType.EXPORT)
    public void export(SysUser query, HttpServletResponse response) throws IOException
    {
        List<SysUser> rows = accountService.selectAccountList(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=archive-account.xlsx");
        ArchiveAccountWorkbook.write(response.getOutputStream(), ArchiveAccountWorkbook.toExportRows(rows), false,
                List.of(), List.of());
    }

    /**
     * 下载导入模板（含填写说明页）。
     *
     * 模板里给「部门名称」「角色名称」加上系统现有的部门/角色下拉，
     * 避免用户把编号或随意数字填进名称列（曾出现「找不到角色「0」」的报错）。
     */
    /**
     * 下载导入模板（含填写说明页）。
     *
     * 模板里额外带一页「可选值」列出当前系统的部门完整路径与角色名称，
     * 并给「部门名称」「角色名称」「是否档案员」「状态」四列加下拉校验。
     * 起因是有用户把数字填进「角色名称」列，导入时才报「找不到角色」，模板本身却看不出该填什么。
     */
    @GetMapping("/template")
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    public void template(HttpServletResponse response) throws IOException
    {
        List<SysDept> depts = accountService.selectDeptTree();
        Map<Long, SysDept> byId = new LinkedHashMap<>();
        for (SysDept dept : depts)
        {
            byId.put(dept.getDeptId(), dept);
        }
        List<String> deptPaths = new ArrayList<>();
        for (SysDept dept : depts)
        {
            if (StringUtils.isNotEmpty(dept.getDeptName()))
            {
                // 用「父 / 子」完整路径，避免不同分支下的同名部门被混淆
                deptPaths.add(deptFullPath(dept, byId));
            }
        }
        // 角色只列启用状态的，与导入时的校验口径一致
        List<String> roleNames = new ArrayList<>();
        for (com.ruoyi.common.core.domain.entity.SysRole role : roleService.selectRoleAll())
        {
            if (role != null && StringUtils.isNotEmpty(role.getRoleName()) && "0".equals(role.getStatus()))
            {
                roleNames.add(role.getRoleName().strip());
            }
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=archive-account-template.xlsx");
        ArchiveAccountWorkbook.write(response.getOutputStream(), List.of(), true, deptPaths, roleNames);
    }

    /**
     * 拼出部门的完整路径，例如「若依科技 / 深圳总公司 / 研发部门」。
     */
    private static String deptFullPath(SysDept dept, Map<Long, SysDept> byId)
    {
        java.util.Deque<String> labels = new java.util.ArrayDeque<>();
        labels.add(StringUtils.strip(dept.getDeptName()));
        Set<Long> seen = new java.util.LinkedHashSet<>();
        seen.add(dept.getDeptId());
        Long parent = dept.getParentId();
        while (parent != null && parent != 0L && seen.add(parent) && byId.containsKey(parent))
        {
            SysDept ancestor = byId.get(parent);
            labels.addFirst(StringUtils.strip(ancestor.getDeptName()));
            parent = ancestor.getParentId();
        }
        return String.join(" / ", labels);
    }

    /**
     * Excel 导入。逐行处理并返回成功数与失败原因。
     */
    @PostMapping("/import")
    @ResponseBody
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.IMPORT)
    public AjaxResult importFile(@RequestParam MultipartFile file)
    {
        List<ArchiveAccountWorkbook.ImportRow> parsed = ArchiveAccountWorkbook.read(file);
        List<IArchiveAccountService.ArchiveImportRow> rows = new ArrayList<>(parsed.size());
        for (ArchiveAccountWorkbook.ImportRow row : parsed)
        {
            rows.add(new IArchiveAccountService.ArchiveImportRow(row.rowNo(), row.userNo(), row.loginName(),
                    row.userName(), row.deptName(), row.roleName(), row.phonenumber(), row.email(),
                    row.archivist(), row.status()));
        }
        Map<String, Object> result = accountService.importAccounts(rows, getLoginName());
        return AjaxResult.success(result);
    }

    /**
     * 绑定二维码。内容只为纯文本用户编号，可通过 responseType=png 直接取图，
     * 也可用 format=base64 取用于页面内嵌。
     */
    @GetMapping("/bindqrcode")
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.EXPORT)
    public void bindQrCode(@RequestParam String userNo,
                           @RequestParam(defaultValue = "png") String format,
                           HttpServletResponse response) throws IOException
    {
        byte[] png = accountService.buildBindQrCode(userNo, 8);
        if ("base64".equalsIgnoreCase(format))
        {
            response.setContentType("application/json;charset=UTF-8");
            String encoded = "data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(png);
            ServletUtils.renderString(response, "{\"code\":0,\"msg\":\"操作成功\",\"data\":{\"userNo\":\""
                    + StringUtils.strip(userNo) + "\",\"image\":\"" + encoded + "\"}}");
            return;
        }
        response.setContentType("image/png");
        // 二维码不允许被中间层缓存，避免拿到别人的编号二维码
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(png);
    }

    @GetMapping("/deptTree")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult deptTree()
    {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        List<SysDept> depts = accountService.selectDeptTree();
        for (SysDept dept : depts)
        {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", dept.getDeptId());
            node.put("parentId", dept.getParentId());
            node.put("name", dept.getDeptName());
            node.put("status", dept.getStatus());
            byId.put(dept.getDeptId(), node);
        }
        for (Map<String, Object> node : byId.values())
        {
            Long parentId = (Long) node.get("parentId");
            Map<String, Object> parent = parentId == null ? null : byId.get(parentId);
            if (parent == null)
            {
                nodes.add(node);
            }
            else
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) parent.computeIfAbsent("children", key -> new ArrayList<>());
                children.add(node);
            }
        }
        return AjaxResult.success(nodes);
    }

    @GetMapping("/roles")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult roles()
    {
        return AjaxResult.success(roleService.selectRoleAll());
    }

    /**
     * 管理员解除微信绑定（联动模块二）。
     */
    @PostMapping("/unbind/{userId}")
    @ResponseBody
    @RequiresPermissions(value = { VIEW, MANAGE }, logical = Logical.AND)
    @Log(title = "档案账号管理", businessType = BusinessType.GRANT)
    public AjaxResult unbind(@PathVariable Long userId)
    {
        boolean done = bindingService.unbindByUser(userId, getLoginName());
        return done ? AjaxResult.success("已解除该账号的微信绑定") : error("该账号当前没有有效绑定");
    }

    /**
     * 查询绑定状态，供详情页显示。
     */
    @GetMapping("/binding/{userId}")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult binding(@PathVariable Long userId)
    {
        Map<String, Object> status = bindingService.bindingStatus(userId);
        if (status == null)
        {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("bound", false);
            return AjaxResult.success(empty);
        }
        return AjaxResult.success(status);
    }

    /**
     * 保存前的编号可用性校验，用于表单即时提示。
     */
    @GetMapping("/checkUserNo")
    @ResponseBody
    @RequiresPermissions(VIEW)
    public AjaxResult checkUserNo(@RequestParam Long userId, @RequestParam String userNo)
    {
        SysUser probe = new SysUser();
        probe.setUserId(userId == null || userId == 0L ? null : userId);
        probe.setUserNo(userNo);
        return AjaxResult.success(accountService.checkUserNoUnique(probe));
    }
}
