package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.security.Md5Utils;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.IArchiveBindingService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.support.ArchiveCryptoUtils;
import com.ruoyi.system.service.support.WxMiniappClient;

/**
 * M12-B 微信绑定与小程序自动登录 服务实现。
 *
 * 关键规则（均对应模块拆分文档第 3 节的四条关键规则）：
 *  1) 绑定必须验账号口令，不允许凭用户编号任意绑定；
 *  2) session_key 不返回客户端、不写日志；
 *  3) 解绑/停用 → bind_version 递增，旧令牌全部失效；
 *  4) 权限数组缺失时默认零权限，只有明确返回 *:*:* 才是全权限。
 *
 * @author member6
 */
@Service
public class ArchiveBindingServiceImpl implements IArchiveBindingService
{
    private static final Logger log = LoggerFactory.getLogger(ArchiveBindingServiceImpl.class);

    /** 小程序令牌有效期（小时），可在 application.yml 用 wx.token.ttl-hours 覆盖 */
    @Value("${wx.token.ttl-hours:168}")
    private int tokenTtlHours;

    /** 业务码：账号已存在有效绑定 */
    private static final int CODE_ALREADY_BOUND = 4091;

    /** 业务码：微信身份未绑定平台账号 */
    private static final int CODE_ACCOUNT_UNBOUND = 4011;

    /** 业务码：账号已停用 */
    private static final int CODE_ACCOUNT_DISABLED = 4031;

    /** 业务码：令牌无效或已过期 */
    private static final int CODE_TOKEN_INVALID = 4012;

    /** 业务码：账号口令不正确 */
    private static final int CODE_PASSWORD_MISMATCH = 4013;

    /** 业务码：用户编号不存在 */
    private static final int CODE_USER_NO_NOT_FOUND = 4041;

    private static final String SELECT_BINDING = """
        select binding_id,user_id,open_id,bind_version,status,bound_at
        from wx_binding
        """;

    private static final RowMapper<Map<String, Object>> BINDING_ROW = (rs, index) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", rs.getLong("binding_id"));
        row.put("userId", rs.getLong("user_id"));
        row.put("openId", rs.getString("open_id"));
        row.put("bindVersion", rs.getInt("bind_version"));
        row.put("status", rs.getInt("status"));
        row.put("boundAt", rs.getTimestamp("bound_at"));
        return row;
    };

    private final JdbcTemplate jdbc;

    private final SysUserMapper userMapper;

    private final ISysMenuService menuService;

    private final WxMiniappClient wxClient;

    public ArchiveBindingServiceImpl(JdbcTemplate jdbc, SysUserMapper userMapper, ISysMenuService menuService,
            WxMiniappClient wxClient)
    {
        this.jdbc = jdbc;
        this.userMapper = userMapper;
        this.menuService = menuService;
        this.wxClient = wxClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String code, String clientType)
    {
        WxMiniappClient.WxSession wx = wxClient.exchange(code);
        Map<String, Object> binding = findActiveBindingByOpenId(wx.openId());
        if (binding == null)
        {
            // 未绑定时只回 bound=false，不回任何账号信息
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bound", false);
            return result;
        }
        SysUser user = requireEnabledUser(toLong(binding.get("userId")));
        int bindVersion = toInt(binding.get("bindVersion"));
        String token = issueToken(user, bindVersion, clientType);
        return buildSession(user, token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(String code, String userNo, String accountPwd, String clientType)
    {
        String no = StringUtils.strip(userNo);
        if (!StringUtils.isNotEmpty(no))
        {
            throw new ArchiveBizException(CODE_USER_NO_NOT_FOUND, "请填写平台用户编号");
        }
        if (StringUtils.isEmpty(accountPwd))
        {
            // 规则 1：不允许凭用户编号任意绑定
            throw new ArchiveBizException(CODE_PASSWORD_MISMATCH, "首次绑定需要校验平台账号口令");
        }
        WxMiniappClient.WxSession wx = wxClient.exchange(code);

        SysUser user = userMapper.selectUserByUserNo(no);
        if (user == null)
        {
            throw new ArchiveBizException(CODE_USER_NO_NOT_FOUND, "用户编号不存在，请核对管理员提供的编号");
        }
        if (UserConstants.USER_DISABLE.equals(user.getStatus()))
        {
            throw new ArchiveBizException(CODE_ACCOUNT_DISABLED, "账号已停用，请联系管理员");
        }
        if (!matchesPassword(user, accountPwd))
        {
            throw new ArchiveBizException(CODE_PASSWORD_MISMATCH, "平台账号口令不正确，请重新输入");
        }
        // 一个 openid 同时只绑一个账号；一个账号同时只允许一条有效绑定
        if (findActiveBindingByOpenId(wx.openId()) != null)
        {
            throw new ArchiveBizException(CODE_ALREADY_BOUND, "当前微信已绑定账号，如需重绑请联系管理员解除旧绑定");
        }
        if (findActiveBindingByUserId(user.getUserId()) != null)
        {
            throw new ArchiveBizException(CODE_ALREADY_BOUND, "该平台账号已绑定其他微信，请联系管理员解除旧绑定");
        }

        int bindVersion = nextBindVersion(user.getUserId());
        jdbc.update("""
            insert into wx_binding(user_id,open_id,union_id,bind_version,status,bound_at,create_by)
            values(?,?,?,?,1,now(),?)
            """, user.getUserId(), wx.openId(), wx.unionId(), bindVersion, user.getLoginName());
        String token = issueToken(user, bindVersion, clientType);
        log.info("账号 {} 完成首次微信绑定", user.getLoginName());
        return buildSession(user, token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> session(String token, String clientType)
    {
        String raw = StringUtils.strip(token);
        if (StringUtils.isEmpty(raw))
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        String digest = ArchiveCryptoUtils.sha256Hex(raw);
        List<Map<String, Object>> rows = jdbc.query("""
            select token_id,user_id,bind_version,expires_at,revoked_at
            from wx_token where token_digest = ?
            """, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tokenId", rs.getLong("token_id"));
            row.put("userId", rs.getLong("user_id"));
            row.put("bindVersion", rs.getInt("bind_version"));
            row.put("expiresAt", rs.getTimestamp("expires_at"));
            row.put("revokedAt", rs.getTimestamp("revoked_at"));
            return row;
        }, digest);
        if (rows.isEmpty())
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        Map<String, Object> row = rows.get(0);
        if (row.get("revokedAt") != null)
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "登录状态已被撤销，请重新登录");
        }
        Date expiresAt = (Date) row.get("expiresAt");
        if (expiresAt == null || expiresAt.toInstant().isBefore(new Date().toInstant()))
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "登录状态已过期，请重新登录");
        }
        Long userId = toLong(row.get("userId"));
        SysUser user = requireEnabledUser(userId);
        int tokenBindVersion = toInt(row.get("bindVersion"));
        int currentBindVersion = currentBindVersion(userId);
        // 规则 3：解绑或停用后版本递增，旧令牌立即失效
        if (tokenBindVersion != currentBindVersion)
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "账号绑定关系已变更，请重新登录");
        }
        // 规则 4：每次会话校验都回最新权限，权限被收回后立即体现在权限数组里
        return buildSession(user, null);
    }

    @Override
    public void logout(String token)
    {
        String raw = StringUtils.strip(token);
        if (StringUtils.isEmpty(raw))
        {
            return;
        }
        jdbc.update("update wx_token set revoked_at = now() where token_digest = ? and revoked_at is null",
                ArchiveCryptoUtils.sha256Hex(raw));
    }

    /**
     * 小程序个人中心资料。
     *
     * 只返回本账号可公开的基础字段；OpenID 只给掩码，session_key、口令、盐一律不出现。
     */
    @Override
    public Map<String, Object> profile(String token, String clientType)
    {
        // 复用 session 校验，保证停用/解绑/过期/版本变化都会先被拦下
        Map<String, Object> session = session(token, clientType);
        Long userId = Long.valueOf(String.valueOf(session.get("userId")));
        SysUser user = userMapper.selectUserById(userId);
        Map<String, Object> result = new LinkedHashMap<>(session);
        result.put("avatar", ""); // 头像路径为空时前端用姓名首字占位
        result.put("userNo", StringUtils.defaultString(user.getUserNo()));
        result.put("loginName", StringUtils.defaultString(user.getLoginName()));
        result.put("deptName", user.getDept() == null ? "" : StringUtils.defaultString(user.getDept().getDeptName()));
        result.put("phonenumber", maskPhone(user.getPhonenumber()));
        result.put("arcArchivist", "1".equals(user.getArcArchivist()));
        Map<String, Object> binding = bindingStatus(userId);
        result.put("bound", binding != null);
        if (binding != null)
        {
            result.put("boundAt", binding.get("boundAt"));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unbindByUser(Long userId, String operator)
    {
        if (userId == null)
        {
            return false;
        }
        int updated = jdbc.update("""
            update wx_binding set status = 0, unbound_at = now(), bind_version = bind_version + 1
            where user_id = ? and status = 1
            """, userId);
        // 无论是否解除到绑定，都撤销该账号全部未撤销令牌，避免残留会话
        revokeAllTokens(userId);
        if (updated > 0)
        {
            log.info("管理员 {} 解除了账号 {} 的微信绑定", operator, userId);
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeForDisabledAccount(Long userId)
    {
        if (userId == null)
        {
            return;
        }
        // 停用不解除绑定本身（恢复后仍可用），但必须让旧令牌立即失效
        jdbc.update("update wx_binding set bind_version = bind_version + 1 where user_id = ? and status = 1", userId);
        revokeAllTokens(userId);
    }

    @Override
    public Map<String, Object> bindingStatus(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.query(SELECT_BINDING + " where user_id = ? and status = 1", BINDING_ROW,
                userId);
        if (rows.isEmpty())
        {
            return null;
        }
        Map<String, Object> binding = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bound", true);
        // OpenID 只回掩码，完整值不出后台
        result.put("openIdMasked", ArchiveCryptoUtils.mask((String) binding.get("openId")));
        result.put("boundAt", binding.get("boundAt"));
        result.put("bindVersion", binding.get("bindVersion"));
        return result;
    }

    @Override
    public int purgeExpiredTokens()
    {
        return jdbc.update("delete from wx_token where expires_at < date_sub(now(), interval 7 day)");
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private Map<String, Object> findActiveBindingByOpenId(String openId)
    {
        List<Map<String, Object>> rows = jdbc.query(SELECT_BINDING + " where open_id = ? and status = 1", BINDING_ROW,
                openId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findActiveBindingByUserId(Long userId)
    {
        List<Map<String, Object>> rows = jdbc.query(SELECT_BINDING + " where user_id = ? and status = 1", BINDING_ROW,
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 取该账号当前绑定版本；没有有效绑定时返回 0。
     */
    private int currentBindVersion(Long userId)
    {
        Map<String, Object> binding = findActiveBindingByUserId(userId);
        return binding == null ? 0 : toInt(binding.get("bindVersion"));
    }

    /**
     * 计算下一次绑定版本：在历史最大值上加 1，保证单调递增（解绑后重绑也不会回到旧值）。
     */
    private int nextBindVersion(Long userId)
    {
        Integer max = jdbc.queryForObject("select coalesce(max(bind_version), 0) from wx_binding where user_id = ?",
                Integer.class, userId);
        return (max == null ? 0 : max) + 1;
    }

    /**
     * 校验账号口令，与若依登录保持同一摘要算法（MD5(loginName + password + salt)）。
     */
    private boolean matchesPassword(SysUser user, String rawPassword)
    {
        String salt = user.getSalt() == null ? "" : user.getSalt();
        String encrypted = Md5Utils.hash(user.getLoginName() + rawPassword + salt);
        String stored = user.getPassword() == null ? "" : user.getPassword();
        return StringUtils.isNotEmpty(stored) && ArchiveCryptoUtils.constantTimeEquals(encrypted, stored);
    }

    /**
     * 签发不透明令牌：库里只存摘要，原文只返回这一次。
     */
    private String issueToken(SysUser user, int bindVersion, String clientType)
    {
        String token = ArchiveCryptoUtils.randomToken(32);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(Math.max(1, tokenTtlHours));
        jdbc.update("""
            insert into wx_token(token_digest,user_id,bind_version,expires_at,client_type)
            values(?,?,?,?,?)
            """, ArchiveCryptoUtils.sha256Hex(token), user.getUserId(), bindVersion,
                Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()),
                StringUtils.isEmpty(clientType) ? "miniapp" : clientType);
        return token;
    }

    private void revokeAllTokens(Long userId)
    {
        jdbc.update("update wx_token set revoked_at = now() where user_id = ? and revoked_at is null", userId);
    }

    /**
     * 读取账号并校验可用性；停用或不存在都拒绝，不映射成「未绑定」。
     */
    private SysUser requireEnabledUser(Long userId)
    {
        SysUser user = userMapper.selectUserById(userId);
        if (user == null)
        {
            throw new ArchiveBizException(CODE_TOKEN_INVALID, "账号不存在，请联系管理员");
        }
        if (UserConstants.USER_DISABLE.equals(user.getStatus()))
        {
            throw new ArchiveBizException(CODE_ACCOUNT_DISABLED, "账号已停用，请联系管理员");
        }
        return user;
    }

    /**
     * 组装会话对象。
     *
     * 权限口径（规则 4）：只有管理员角色才给 *:*:*，其余角色取菜单权限并集；
     * 取不到时返回空数组而不是 null，避免前端「权限数组缺失 = 全权限」的错误解读。
     */
    private Map<String, Object> buildSession(SysUser user, String token)
    {
        Map<String, Object> session = new LinkedHashMap<>();
        if (StringUtils.isNotEmpty(token))
        {
            session.put("token", token);
        }
        session.put("userId", String.valueOf(user.getUserId()));
        session.put("userName", StringUtils.defaultIfEmpty(user.getUserName(), user.getLoginName()));
        session.put("roleName", primaryRoleName(user));
        session.put("permissions", permissionsOf(user));
        session.put("userNo", StringUtils.defaultString(user.getUserNo()));
        return session;
    }

    private String primaryRoleName(SysUser user)
    {
        if (user.isAdmin())
        {
            return "超级管理员";
        }
        List<SysRole> roles = user.getRoles();
        if (roles == null || roles.isEmpty())
        {
            return "平台用户";
        }
        for (SysRole role : roles)
        {
            if (role != null && UserConstants.ROLE_NORMAL.equals(role.getStatus())
                    && StringUtils.isNotEmpty(role.getRoleName()))
            {
                return role.getRoleName();
            }
        }
        return "平台用户";
    }

    private String[] permissionsOf(SysUser user)
    {
        if (user.isAdmin())
        {
            return new String[] { "*:*:*" };
        }
        Set<String> perms = menuService.selectPermsByUserId(user.getUserId());
        if (perms == null || perms.isEmpty())
        {
            return new String[0];
        }
        return perms.toArray(new String[0]);
    }

    private static Long toLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /**
     * 手机号掩码：个人中心只展示中间四位以外的部分，避免完整号码出现在页面上。
     */
    private static String maskPhone(String phone)
    {
        String value = StringUtils.strip(phone);
        if (value == null || value.length() < 7)
        {
            return "";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static int toInt(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}
