package com.ruoyi.system.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.qrcode.QrCodeEncoder;
import com.ruoyi.common.utils.security.Md5Utils;
import com.ruoyi.system.domain.SysUserOnline;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.IArchiveAccountService;
import com.ruoyi.system.service.IArchiveBindingService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserOnlineService;
import com.ruoyi.system.service.ISysUserService;

/**
 * M12-A 账号与组织底座 服务实现。
 *
 * 边界（模块拆分文档第 0 节）：
 *  - 复用若依 sys_user / sys_dept / sys_role，不新建平行账号体系；
 *  - 密级判定规则归 M13（成员1），这里只保存 arcSecretLevel 引用，不参与判定；
 *  - 「删除」一律实现为停用，记录与审计保留、可恢复。
 *
 * 实现要点：
 *  1) 清单查询统一走 {@link SysUserMapper#selectUserList}，复用若依数据范围过滤；
 *  2) 统计与明细共用同一个查询条件对象，保证「图表数字 = 表格数字」；
 *  3) 绑定二维码内容只放纯文本用户编号（例如 0003），保留前导零，不含任何个人资料；
 *  4) 口令摘要沿用若依算法 MD5(loginName + password + salt)，此处直接用 Md5Utils 计算，
 *     避免 ruoyi-system 反向依赖 ruoyi-framework 里的 SysPasswordService（模块间会形成环）。
 *
 * @author member6
 */
@Service
public class ArchiveAccountServiceImpl implements IArchiveAccountService
{
    /** 用户编号允许的字符集，与小程序端 utils/qr.js 的 parseUserNo 保持一致 */
    private static final Pattern USER_NO_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 越权改动他人资料的业务码，按项目统一业务码表 3001 */
    private static final int CODE_FORBIDDEN = 3001;

    /** M12：账号模块只开放这两个业务角色（超级管理员角色永远不在此列） */
    private static final Set<String> ASSIGNABLE_ROLE_KEYS = Set.of("common", "arc_admin");

    /** 档案管理员角色标识，用于“最后一个管理员”保护 */
    private static final String ARCHIVE_ADMIN_ROLE_KEY = "arc_admin";

    private final SysUserMapper userMapper;

    private final ISysUserService userService;

    private final ISysRoleService roleService;

    private final ISysDeptService deptService;

    private final ISysUserOnlineService userOnlineService;

    private final IArchiveBindingService bindingService;

    private final JdbcTemplate jdbc;

    public ArchiveAccountServiceImpl(SysUserMapper userMapper, ISysUserService userService,
            ISysRoleService roleService, ISysDeptService deptService, ISysUserOnlineService userOnlineService,
            IArchiveBindingService bindingService, JdbcTemplate jdbc)
    {
        this.userMapper = userMapper;
        this.userService = userService;
        this.roleService = roleService;
        this.deptService = deptService;
        this.userOnlineService = userOnlineService;
        this.bindingService = bindingService;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // 查询与统计
    // ------------------------------------------------------------------

    /**
     * 账号清单组合查询。
     *
     * 可用条件：姓名、部门（含下级）、角色、状态、用户编号、档案员标识，任意两项即可组合。
     * 查询条件由 Controller 装配，本方法只负责执行，导出与页面走同一个方法，
     * 避免出现「页面查一套、导出查一套」的数字不一致。
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectAccountList(SysUser query)
    {
        // 1 号内置管理员不在档案模块展示（清单、统计、导出同一口径）
        excludeBuiltInAdmin(query);
        List<SysUser> rows = userMapper.selectUserList(query);
        fillListExtras(rows);
        return rows;
    }

    /**
     * 补齐清单页需要的两项关联数据：微信绑定标记与角色列表。
     *
     * 为什么不直接在主查询里 join 角色表：sys_user_role 对多角色账号会放大行数，
     * 会把 PageHelper 的 total 算多，也会让「一个账号一行」的清单变成多行。
     * 因此这里对当前这一页的账号做两次批量查询再回填，既不多算分页，也不产生 N+1。
     */
    private void fillListExtras(List<SysUser> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return;
        }
        List<Object> ids = new ArrayList<>(rows.size());
        for (SysUser user : rows)
        {
            user.setWxBound("0");
            ids.add(user.getUserId());
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] arguments = ids.toArray();

        // 已绑定微信的账号
        Set<Long> boundIds = new LinkedHashSet<>(jdbc.queryForList(
                "select distinct user_id from wx_binding where status = 1 and user_id in (" + placeholders + ")",
                Long.class, arguments));
        Map<Long, SysUser> byId = new LinkedHashMap<>();
        for (SysUser user : rows)
        {
            if (boundIds.contains(user.getUserId()))
            {
                user.setWxBound("1");
            }
            byId.put(user.getUserId(), user);
        }

        // 角色列表：清单的「角色」列与统计的「按角色」分布都依赖它
        jdbc.query("""
            select ur.user_id, r.role_id, r.role_name, r.role_key, r.status
            from sys_user_role ur
            join sys_role r on r.role_id = ur.role_id
            where ur.user_id in (""" + placeholders + ") order by r.role_sort, r.role_id",
            rs -> {
                SysUser user = byId.get(rs.getLong("user_id"));
                if (user == null)
                {
                    return;
                }
                if (user.getRoles() == null)
                {
                    user.setRoles(new ArrayList<>());
                }
                SysRole role = new SysRole();
                role.setRoleId(rs.getLong("role_id"));
                role.setRoleName(rs.getString("role_name"));
                role.setRoleKey(rs.getString("role_key"));
                role.setStatus(rs.getString("status"));
                user.getRoles().add(role);
            }, arguments);
    }

    @Override
    public Map<String, Object> selectAccountDetail(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先选择要查看的账号");
        }
        SysUser user = userMapper.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("账号不存在，请刷新列表");
        }
        SysDept dept = user.getDept();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userId", user.getUserId());
        detail.put("userNo", StringUtils.defaultString(user.getUserNo()));
        detail.put("loginName", StringUtils.defaultString(user.getLoginName()));
        detail.put("userName", StringUtils.defaultString(user.getUserName()));
        detail.put("deptId", user.getDeptId());
        detail.put("deptName", dept == null ? "" : StringUtils.defaultString(dept.getDeptName()));
        detail.put("phonenumber", StringUtils.defaultString(user.getPhonenumber()));
        detail.put("email", StringUtils.defaultString(user.getEmail()));
        detail.put("sex", user.getSex());
        // 状态：0 正常，1 停用。停用即等同「删除」，历史记录保留供审计
        detail.put("status", user.getStatus());
        detail.put("statusLabel", UserConstants.NORMAL.equals(user.getStatus()) ? "正常" : "停用");
        detail.put("arcArchivist", StringUtils.defaultString(user.getArcArchivist(), "0"));
        detail.put("arcSecretLevel", StringUtils.defaultString(user.getArcSecretLevel()));
        detail.put("loginIp", StringUtils.defaultString(user.getLoginIp()));
        detail.put("loginDate", user.getLoginDate());
        detail.put("createBy", StringUtils.defaultString(user.getCreateBy()));
        detail.put("createTime", user.getCreateTime());
        detail.put("remark", StringUtils.defaultString(user.getRemark()));
        detail.put("roleNames", joinRoleNames(user.getRoles()));
        // 编辑表单需要回显角色勾选状态
        detail.put("roleIds", user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(SysRole::getRoleId).toList());
        detail.put("wxBound", isWxBound(userId));
        detail.put("onlineSessions", countOnlineSessions(user.getLoginName()));
        return detail;
    }

    /**
     * 账号统计：总数、启用/停用、档案员数，以及按部门、按角色的分布。
     *
     * 统计口径与明细完全一致——都基于 {@link #selectAccountList(SysUser)} 的同一批数据，
     * 因此页面「图表数字」与「明细行数」天然相同，不会出现两套 SQL 结果打架。
     * 注意：一个账号可挂多个角色，所以「按角色」各项之和大于账号总数，这是预期口径，
     * 页面上会把这一点写进口径说明。
     */
    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public Map<String, Object> selectAccountStatistics(SysUser query)
    {
        // 与清单同一口径：排除 1 号内置管理员，避免“清单看不到、总数却多一个”
        excludeBuiltInAdmin(query);
        List<SysUser> rows = userMapper.selectUserList(query);
        // 统计必须与清单同源：角色、绑定标记都由同一个 fillListExtras 补齐，
        // 否则明细「角色」列有值而「按角色」图表全是「未分配角色」，两个数字对不上。
        fillListExtras(rows);
        int active = 0;
        int archivist = 0;
        int bound = 0;
        Map<String, Integer> byDept = new LinkedHashMap<>();
        Map<String, Integer> byRole = new LinkedHashMap<>();
        for (SysUser user : rows)
        {
            if (UserConstants.NORMAL.equals(user.getStatus()))
            {
                active++;
            }
            if ("1".equals(user.getArcArchivist()))
            {
                archivist++;
            }
            SysDept dept = user.getDept();
            String deptName = dept == null || StringUtils.isEmpty(dept.getDeptName())
                    ? "未分配部门" : dept.getDeptName();
            byDept.merge(deptName, 1, Integer::sum);
            if (user.getRoles() == null || user.getRoles().isEmpty())
            {
                byRole.merge("未分配角色", 1, Integer::sum);
            }
            else
            {
                for (SysRole role : user.getRoles())
                {
                    if (role != null && StringUtils.isNotEmpty(role.getRoleName()))
                    {
                        byRole.merge(role.getRoleName(), 1, Integer::sum);
                    }
                }
            }
        }
        // 绑定数直接用 fillListExtras 已回填的 wxBound，不再重复查库
        for (SysUser user : rows)
        {
            if ("1".equals(user.getWxBound()))
            {
                bound++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("active", active);
        result.put("disabled", rows.size() - active);
        result.put("archivist", archivist);
        result.put("wxBound", bound);
        result.put("byDept", toEntries(byDept));
        result.put("byRole", toEntries(byRole));
        // 状态分布用 LinkedHashMap 保证「正常、停用」的显示顺序稳定
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("正常", active);
        byStatus.put("停用", rows.size() - active);
        result.put("byStatus", toEntries(byStatus));
        return result;
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /**
     * 保存账号（新增或修改）。
     *
     * 新增：编号格式与唯一性 → 登录账号唯一 → 部门必选 → 写入 → 建立角色关联。
     * 修改：只更新基础资料、部门与角色；口令与密级走各自入口，避免在编辑表单里顺带改敏感字段。
     * 「删除」不在此提供，统一走 {@link #changeStatus(Long, String, String)} 停用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccount(SysUser change, String operator)
    {
        if (change == null)
        {
            throw new ServiceException("请填写账号信息");
        }
        // 1 号账号是若依内置管理员，菜单与权限不依赖角色，档案模块不提供修改入口
        if (change.getUserId() != null && change.getUserId().longValue() == 1L)
        {
            throw new ServiceException("系统内置管理员账号不支持在档案模块修改");
        }
        String userNo = StringUtils.strip(change.getUserNo());
        if (!isValidUserNo(userNo))
        {
            throw new ServiceException("用户编号须为1-64位字母、数字、下划线或连字符");
        }
        change.setUserNo(userNo);
        change.setArcArchivist("1".equals(change.getArcArchivist()) ? "1" : "0");
        change.setArcSecretLevel(StringUtils.strip(change.getArcSecretLevel()));
        change.setUserName(StringUtils.strip(change.getUserName()));
        change.setPhonenumber(StringUtils.strip(change.getPhonenumber()));
        change.setEmail(StringUtils.strip(change.getEmail()));

        boolean creating = change.getUserId() == null;
        if (creating)
        {
            if (StringUtils.isEmpty(change.getLoginName()))
            {
                throw new ServiceException("登录账号不能为空");
            }
            if (change.getDeptId() == null || change.getDeptId() == 0L)
            {
                throw new ServiceException("请选择所属部门");
            }
        }
        else
        {
            // 越权改他人资料统一用业务码 3001
            checkManageAllowed(change);
        }
        // 账号必须有角色：零角色账号登录后菜单为空，是最常见的问题来源
        Long[] roleIds = change.getRoleIds();
        if (roleIds == null || roleIds.length == 0)
        {
            throw new ServiceException("请至少选择一个角色");
        }
        checkRolesAssignable(roleIds);
        SysUser noOwner = userMapper.checkUserNoUnique(userNo);
        if (noOwner != null && !Objects.equals(noOwner.getUserId(), change.getUserId()))
        {
            throw new ServiceException(describeUserNoConflict(noOwner, userNo));
        }
        if (!userService.checkLoginNameUnique(change))
        {
            throw new ServiceException("登录账号「" + change.getLoginName() + "」已存在");
        }
        if (StringUtils.isNotEmpty(change.getPhonenumber()) && !userService.checkPhoneUnique(change))
        {
            throw new ServiceException("手机号码已存在");
        }
        if (StringUtils.isNotEmpty(change.getEmail()) && !userService.checkEmailUnique(change))
        {
            throw new ServiceException("邮箱账号已存在");
        }
        if (change.getDeptId() != null && change.getDeptId() != 0L)
        {
            deptService.checkDeptDataScope(change.getDeptId());
        }
        if (change.getRoleIds() != null && change.getRoleIds().length > 0)
        {
            roleService.checkRoleDataScope(change.getRoleIds());
        }

        change.setUpdateBy(operator);
        if (creating)
        {
            change.setCreateBy(operator);
            change.setUserType(UserConstants.SYSTEM_USER_TYPE);
            change.setStatus(UserConstants.NORMAL);
            // 新账号初始口令等于登录账号，页面会提示首次登录后立即修改
            String salt = ShiroUtils.randomSalt();
            change.setSalt(salt);
            change.setPassword(Md5Utils.hash(change.getLoginName() + change.getLoginName() + salt));
            change.setPwdUpdateDate(new Date());
            userService.insertUser(change);
            return change.getUserId();
        }
        // 修改路径一律不带状态：若允许在编辑接口里改状态，就能绕过
        // changeStatus 里的「停用即撤销小程序令牌」处理，留下停用后仍可用数小时的漏洞。
        // 状态只能通过 PUT /archive/account/status 变更。
        change.setStatus(null);
        userService.updateUser(change);
        return change.getUserId();
    }

    /**
     * 启用/停用账号。
     *
     * 停用即等价于「删除」：记录保留、可恢复、审计可查；同时递增绑定版本并撤销小程序令牌，
     * 使被停用账号的移动端会话立即失效（与模块二的令牌机制联动）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long userId, String status, String operator)
    {
        if (userId == null || (!UserConstants.NORMAL.equals(status) && !UserConstants.USER_DISABLE.equals(status)))
        {
            throw new ServiceException("请选择要执行的状态");
        }
        // 1 号账号是系统内置管理员，停用后无人能恢复，直接拦住
        if (userId.longValue() == 1L)
        {
            throw new ServiceException("系统内置管理员账号不允许停用");
        }
        SysUser target = userMapper.selectUserById(userId);
        if (target == null)
        {
            throw new ServiceException("账号不存在，请刷新列表");
        }
        checkManageAllowed(target);
        if (UserConstants.USER_DISABLE.equals(status) && Objects.equals(currentUserId(), userId))
        {
            throw new ServiceException("不能停用当前登录账号");
        }
        target.setStatus(status);
        target.setUpdateBy(operator);
        userService.changeStatus(target);
        if (UserConstants.USER_DISABLE.equals(status))
        {
            // 停用后移动端会话必须立即失效，不能等自然过期
            bindingService.revokeForDisabledAccount(userId);
        }
    }

    /**
     * 删除账号。
     *
     * 语义：逻辑删除（del_flag=2）+ 解除微信绑定 + 撤销全部小程序令牌 + 清角色/岗位关联。
     * 记录仍保留在 sys_user，可按“已删除”追溯；编号按现有策略不回收。
     *
     * 三道保护：1 号内置管理员不可删；非本人资料需数据范围允许；最后一个档案管理员不可删。
     *
     * @return true 表示删除的是当前登录账号（调用方需要让前端退出登录）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAccount(Long userId, String operator)
    {
        if (userId == null)
        {
            throw new ServiceException("请先选择要删除的账号");
        }
        if (userId.longValue() == 1L)
        {
            throw new ServiceException("系统内置管理员账号不允许删除");
        }
        SysUser target = userMapper.selectUserById(userId);
        if (target == null)
        {
            throw new ServiceException("账号不存在，请刷新列表");
        }
        userService.checkUserDataScope(userId);
        // 至少保留一个可用档案管理员，避免把自己删成“没人能管账号”
        if (holdsArchiveAdmin(userId) && countEnabledArchiveAdmins() <= 1)
        {
            throw new ServiceException("请先指定另一位档案管理员，再删除当前账号");
        }
        boolean self = Objects.equals(currentUserId(), userId);
        // 内部已完成：解除微信绑定、撤销全部令牌、清角色与岗位关联、逻辑删除
        userService.deleteUserById(userId);
        return self;
    }

    /**
     * 批量导入账号。
     *
     * 逐行独立处理：统计成功数与失败行号/原因，不做整批静默回滚。
     * 这样管理员一次就能看到所有问题行，而不是「有一条错就全部没了」，
     * 对应模块拆分文档里「逐行校验 → 成功/失败数 + 失败原因」的验收要求。
     */
    @Override
    public Map<String, Object> importAccounts(List<ArchiveImportRow> rows, String operator)
    {
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("没有可导入的账号数据");
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (ArchiveImportRow row : rows)
        {
            try
            {
                if (importOne(row, operator))
                {
                    created++;
                }
                else
                {
                    updated++;
                }
            }
            catch (ServiceException e)
            {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", row.rowNo());
                failure.put("userNo", row.userNo());
                failure.put("loginName", row.loginName());
                failure.put("reason", e.getMessage());
                failures.add(failure);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("success", created + updated);
        result.put("created", created);
        result.put("updated", updated);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    /**
     * 导入单行。返回 true 表示新增，false 表示更新；校验失败抛 ServiceException。
     */
    private boolean importOne(ArchiveImportRow row, String operator)
    {
        String userNo = StringUtils.strip(row.userNo());
        String loginName = StringUtils.strip(row.loginName());
        if (StringUtils.isEmpty(loginName))
        {
            throw new ServiceException("登录账号不能为空");
        }
        if (!isValidUserNo(userNo))
        {
            throw new ServiceException("用户编号须为1-64位字母、数字、下划线或连字符");
        }
        SysUser existing = userMapper.selectUserByLoginName(loginName);
        SysUser change = new SysUser();
        change.setUserNo(userNo);
        change.setLoginName(loginName);
        change.setUserName(StringUtils.strip(row.userName()));
        change.setPhonenumber(StringUtils.strip(row.phonenumber()));
        change.setEmail(StringUtils.strip(row.email()));
        change.setArcArchivist("1".equals(row.archivist()) ? "1" : "0");
        change.setRemark("由账号清单导入");

        if (StringUtils.isNotEmpty(row.deptName()))
        {
            change.setDeptId(requireDeptIdByName(row.deptName()));
        }
        else if (existing == null)
        {
            throw new ServiceException("新增账号必须填写部门名称");
        }
        else
        {
            change.setDeptId(existing.getDeptId());
        }
        change.setRoleIds(resolveRoleIds(row.roleName(), existing));
        // 导入新增时如果“角色名称”留空，默认给普通角色，避免导出一批没有菜单的账号
        if (existing == null && (change.getRoleIds() == null || change.getRoleIds().length == 0))
        {
            change.setRoleIds(defaultCommonRoleIds());
        }

        if (existing != null)
        {
            change.setUserId(existing.getUserId());
            // 导入不改状态与口令，只更新基础资料
            change.setStatus(null);
            SysUser noOwner = userMapper.checkUserNoUnique(userNo);
            if (noOwner != null && !Objects.equals(noOwner.getUserId(), change.getUserId()))
            {
                throw new ServiceException(describeUserNoConflict(noOwner, userNo));
            }
            change.setUpdateBy(operator);
            userService.updateUser(change);
            return false;
        }
        change.setStatus(UserConstants.NORMAL);
        change.setCreateBy(operator);
        saveAccount(change, operator);
        return true;
    }

    // ------------------------------------------------------------------
    // 绑定二维码与校验
    // ------------------------------------------------------------------

    /**
     * 生成绑定二维码。
     *
     * 安全约定：二维码内容只为纯文本用户编号，绝不放姓名、手机号或令牌，
     * 因此二维码即使被他人看到，也只泄露一个对外编号；真正绑定仍需服务端校验账号口令。
     *
     * 编码与渲染都交给 {@link QrCodeEncoder}（内部使用 ZXing），本方法只负责取编号、校验与落图。
     */
    @Override
    public byte[] buildBindQrCode(String userNo, int scale)
    {
        String no = StringUtils.strip(userNo);
        if (!isValidUserNo(no))
        {
            throw new ServiceException("用户编号格式不正确，只允许字母、数字、下划线、连字符，长度1-64位");
        }
        if (userMapper.selectUserByUserNo(no) == null)
        {
            throw new ServiceException("用户编号不存在，请先在账号管理中确认编号");
        }
        return QrCodeEncoder.toPng(no, scale);
    }

    @Override
    public boolean checkUserNoUnique(SysUser user)
    {
        Long userId = user.getUserId() == null ? -1L : user.getUserId();
        SysUser existing = userMapper.checkUserNoUnique(StringUtils.strip(user.getUserNo()));
        return existing == null || existing.getUserId().longValue() == userId.longValue();
    }

    @Override
    public boolean isValidUserNo(String userNo)
    {
        return StringUtils.isNotEmpty(userNo) && USER_NO_PATTERN.matcher(userNo.strip()).matches();
    }

    /**
     * 自动取号：从 0001 起找第一个没有被占用的编号。
     *
     * 必须把 del_flag=2 的历史账号算进来——数据库的 uk_sys_user_user_no 是整表唯一索引，
     * 已删除的行同样占着编号；否则会出现“系统给我 0003，一保存就报占用”的死循环。
     */
    @Override
    public String nextUserNo()
    {
        List<String> existing = userMapper.selectAllUserNos();
        Set<String> taken = new HashSet<>();
        if (existing != null)
        {
            for (String no : existing)
            {
                if (StringUtils.isNotEmpty(no))
                {
                    taken.add(no.strip());
                }
            }
        }
        for (int i = 1; i <= 9999; i++)
        {
            String candidate = String.format("%04d", i);
            if (!taken.contains(candidate))
            {
                return candidate;
            }
        }
        throw new ServiceException("4 位编号已用尽，请手工指定更长的编号");
    }

    @Override
    public String explainUserNoConflict(String userNo, Long userId)
    {
        String no = StringUtils.strip(userNo);
        if (!isValidUserNo(no))
        {
            return "用户编号须为1-64位字母、数字、下划线或连字符";
        }
        SysUser owner = userMapper.checkUserNoUnique(no);
        if (owner == null || Objects.equals(owner.getUserId(), userId))
        {
            return null;
        }
        return describeUserNoConflict(owner, no);
    }

    @Override
    public boolean isWxBound(Long userId)
    {
        // 绑定业务归模块二，这里只读有效绑定条数用于详情展示
        return userId != null && countBinding(userId) > 0;
    }

    @Override
    public List<SysDept> selectDeptTree()
    {
        return deptService.selectDeptList(new SysDept());
    }

    /**
     * 角色白名单校验：只允许“普通角色(common)”与“档案管理员(arc_admin)”。
     *
     * 前端下拉已在 Controller 过滤，这里再做一次，防止有人直接构造请求塞超管角色。
     */
    private void checkRolesAssignable(Long[] roleIds)
    {
        for (Long roleId : roleIds)
        {
            if (roleId == null)
            {
                throw new ServiceException("角色参数不正确");
            }
            if (roleId.longValue() == 1L)
            {
                throw new ServiceException("超级管理员角色只能由系统内置 1 号账号持有");
            }
            SysRole role = roleService.selectRoleById(roleId);
            if (role == null)
            {
                throw new ServiceException("角色不存在，请刷新页面后重试");
            }
            if (!ASSIGNABLE_ROLE_KEYS.contains(role.getRoleKey()))
            {
                throw new ServiceException("角色只支持“普通角色”与“档案管理员”");
            }
            if (!UserConstants.NORMAL.equals(role.getStatus()))
            {
                throw new ServiceException("角色“" + role.getRoleName() + "”已停用，请先在角色管理中启用");
            }
        }
    }

    /**
     * 导入新增时若没填角色，默认给“普通角色”，避免导入出一批“零角色、登录没菜单”的账号。
     */
    private Long[] defaultCommonRoleIds()
    {
        for (SysRole role : roleService.selectRoleAll())
        {
            if (role != null && "common".equals(role.getRoleKey()) && UserConstants.NORMAL.equals(role.getStatus()))
            {
                return new Long[] { role.getRoleId() };
            }
        }
        throw new ServiceException("系统缺少“普通角色”，请先在角色管理中创建后再导入");
    }

    /**
     * 该账号是否持有档案管理员角色。
     */
    private boolean holdsArchiveAdmin(Long userId)
    {
        Integer count = jdbc.queryForObject("""
                select count(1) from sys_user_role ur
                join sys_role r on r.role_id = ur.role_id
                where ur.user_id = ? and r.role_key = ? and r.del_flag = '0'
                """, Integer.class, userId, ARCHIVE_ADMIN_ROLE_KEY);
        return count != null && count > 0;
    }

    /**
     * 当前可用（未删除、未停用）的档案管理员数量，用于“最后一个管理员不可删”保护。
     */
    private long countEnabledArchiveAdmins()
    {
        Long count = jdbc.queryForObject("""
                select count(distinct u.user_id) from sys_user u
                join sys_user_role ur on ur.user_id = u.user_id
                join sys_role r on r.role_id = ur.role_id
                where u.del_flag = '0' and u.status = '0' and r.del_flag = '0' and r.role_key = ?
                """, Long.class, ARCHIVE_ADMIN_ROLE_KEY);
        return count == null ? 0L : count;
    }

    /**
     * 编号冲突文案：区分“被现有账号占用”和“属于已删除账号（编号不回收）”。
     */
    private static String describeUserNoConflict(SysUser owner, String userNo)
    {
        String who = StringUtils.isEmpty(owner.getLoginName()) ? "未知账号" : StringUtils.strip(owner.getLoginName());
        if ("2".equals(owner.getDelFlag()))
        {
            return "用户编号「" + userNo + "」属于已删除账号（登录名 " + who + "）。编号不回收，请点“重新取号”或换一个编号";
        }
        return "用户编号「" + userNo + "」已被账号 " + who + " 使用，请换一个编号";
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 档案模块统一排除系统内置 1 号账号：清单、统计、导出都不展示它，
     * 避免被误停用/误删（后端另有 1 号账号禁改、禁停、禁删的保护）。
     */
    private void excludeBuiltInAdmin(SysUser query)
    {
        if (query == null)
        {
            return;
        }
        Map<String, Object> params = query.getParams();
        if (params == null)
        {
            params = new HashMap<>();
            query.setParams(params);
        }
        params.put("excludeUserId", 1L);
    }

    /**
     * 校验当前登录账号能否改动目标账号：不能越权改他人资料（业务码 3001）。
     */
    private void checkManageAllowed(SysUser target)
    {
        if (target == null || target.getUserId() == null)
        {
            throw new ServiceException("请先选择要操作的账号");
        }
        SysUser current = ShiroUtils.getSysUser();
        if (current == null)
        {
            throw new ServiceException("登录状态已失效，请重新登录");
        }
        boolean self = Objects.equals(current.getUserId(), target.getUserId());
        // 本人可改自己；1 号内置管理员与“档案管理员(arc_admin)”可改他人（后者仍受数据范围约束），
        // 其余角色只能看不能改——按钮本身也由 archive:account:manage 权限控制。
        if (!self && !current.isAdmin() && !holdsArchiveAdmin(current.getUserId()))
        {
            throw new ArchiveBizException(CODE_FORBIDDEN, "没有修改其他账号资料的权限");
        }
        userService.checkUserAllowed(target);
        userService.checkUserDataScope(target.getUserId());
    }

    /**
     * 按部门名称解析部门ID，支持完整路径匹配（如「若依科技 / 深圳总公司 / 研发部门」）。
     */
    private Long requireDeptIdByName(String deptName)
    {
        String target = StringUtils.strip(deptName);
        List<SysDept> depts = deptService.selectDeptList(new SysDept());
        Map<Long, SysDept> byId = new LinkedHashMap<>();
        for (SysDept dept : depts)
        {
            byId.put(dept.getDeptId(), dept);
        }
        for (SysDept dept : depts)
        {
            if (target.equals(StringUtils.strip(dept.getDeptName())) || target.equals(deptPath(dept, byId)))
            {
                return dept.getDeptId();
            }
        }
        StringBuilder hint = new StringBuilder("找不到部门「").append(deptName).append("」");
        if (StringUtils.strip(deptName).chars().allMatch(Character::isDigit))
        {
            hint.append("：该列应填部门名称，不是编号");
        }
        hint.append("，请核对「部门管理」中的名称后重试");
        throw new ServiceException(hint.toString());
    }

    private static String deptPath(SysDept dept, Map<Long, SysDept> byId)
    {
        Deque<String> labels = new ArrayDeque<>();
        labels.add(StringUtils.strip(dept.getDeptName()));
        Set<Long> seen = new LinkedHashSet<>();
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
     * 按角色名称解析角色IDs。多个角色用顿号、逗号、分号或空白分隔。
     * 留空时：新增账号视为无角色（需管理员后续授权），更新账号保留原角色。
     */
    private Long[] resolveRoleIds(String roleName, SysUser existing)
    {
        if (StringUtils.isEmpty(roleName))
        {
            if (existing == null)
            {
                return new Long[0];
            }
            return roleService.selectRolesByUserId(existing.getUserId()).stream()
                    .map(SysRole::getRoleId).toArray(Long[]::new);
        }
        Map<String, SysRole> byName = new LinkedHashMap<>();
        for (SysRole role : roleService.selectRoleAll())
        {
            if (role != null && StringUtils.isNotEmpty(role.getRoleName()))
            {
                byName.put(StringUtils.strip(role.getRoleName()), role);
            }
        }
        List<Long> resolved = new ArrayList<>();
        for (String part : roleName.split("[、,;；\\s]+"))
        {
            String name = StringUtils.strip(part);
            if (name.isEmpty())
            {
                continue;
            }
            SysRole role = byName.get(name);
            if (role == null)
            {
                // 常见错误：把角色编号或随意数字填进「角色名称」列，
                // 提示里直接列出可选角色名，比只说「找不到」好排查。
                StringBuilder hint = new StringBuilder();
                hint.append("找不到角色「").append(name).append("」");
                if (name.chars().allMatch(Character::isDigit))
                {
                    hint.append("：该列应填角色名称，不是编号");
                }
                hint.append("。可用角色：").append(String.join("、", byName.keySet()));
                throw new ServiceException(hint.toString());
            }
            if (!resolved.contains(role.getRoleId()))
            {
                resolved.add(role.getRoleId());
            }
        }
        if (resolved.isEmpty())
        {
            throw new ServiceException("角色名称格式不正确");
        }
        return resolved.toArray(new Long[0]);
    }

    /**
     * 统计该账号当前有效的微信绑定条数（只读，不写绑定状态）。
     */
    private int countBinding(Long userId)
    {
        Integer count = jdbc.queryForObject(
                "select count(1) from wx_binding where user_id = ? and status = 1", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countOnlineSessions(String loginName)
    {
        if (StringUtils.isEmpty(loginName))
        {
            return 0;
        }
        SysUserOnline probe = new SysUserOnline();
        probe.setLoginName(loginName);
        List<SysUserOnline> sessions = userOnlineService.selectUserOnlineList(probe);
        return sessions == null ? 0 : sessions.size();
    }

    private static Long currentUserId()
    {
        SysUser current = ShiroUtils.getSysUser();
        return current == null ? null : current.getUserId();
    }

    private static String joinRoleNames(List<SysRole> roles)
    {
        if (roles == null || roles.isEmpty())
        {
            return "";
        }
        Set<String> names = new LinkedHashSet<>();
        for (SysRole role : roles)
        {
            if (role != null && StringUtils.isNotEmpty(role.getRoleName()))
            {
                names.add(role.getRoleName());
            }
        }
        return String.join("、", names);
    }

    private static List<Map<String, Object>> toEntries(Map<String, Integer> counts)
    {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            entries.add(item);
        }
        return entries;
    }
}
