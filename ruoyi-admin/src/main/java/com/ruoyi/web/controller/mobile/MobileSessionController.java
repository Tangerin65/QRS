package com.ruoyi.web.controller.mobile;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.IArchiveBindingService;

/**
 * M12-B 小程序会话校验接口。
 *
 * GET /api/mobile/auth/session
 *   请求头：Authorization: Bearer &lt;token&gt;
 *   返回：会话对象（含最新权限）
 *
 * 这个接口是小程序每次启动/回到前台的「真实身份复核」入口，因此必须：
 *  - 令牌在库中存在、未撤销、未过期；
 *  - 账号未被停用、未被逻辑删除；
 *  - 令牌里的 bind_version 与该账号当前绑定版本一致（解绑后旧令牌立即失效）；
 *  - 权限数组每次都取最新值，权限被收回后前端菜单同步收敛。
 *
 * 任一条件不满足都返回 401（ACCOUNT_UNBOUND / TOKEN_INVALID）或 403（ACCOUNT_DISABLED），
 * 绝不映射成「未绑定」，否则前端会把停用账号当成可重新绑定的新账号。
 *
 * @author member6
 */
@RestController
@RequestMapping("/api/mobile/auth")
public class MobileSessionController extends MobileApiSupport
{
    private static final String CLIENT_TYPE = "miniapp";

    private final IArchiveBindingService bindingService;

    public MobileSessionController(IArchiveBindingService bindingService)
    {
        this.bindingService = bindingService;
    }

    /**
     * 校验令牌并返回最新会话。
     *
     * 说明：成员3 的前端允许「省略 token 沿用当前令牌」，但服务端不能接受空令牌
     * 推断身份——这里要求必须带 Bearer 令牌，缺失即 401，让前端走重新登录流程。
     */
    @Anonymous
    @GetMapping("/session")
    public Object session(HttpServletRequest request)
    {
        String token = bearerToken(request);
        if (StringUtils.isEmpty(token))
        {
            throw new ArchiveBizException(4011, "尚未登录或缺少登录凭证，请重新登录");
        }
        Map<String, Object> session = bindingService.session(token, CLIENT_TYPE);
        return ok(session);
    }

    /**
     * 个人中心资料：头像占位、基础资料、绑定状态。
     *
     * 与 session 共用同一套令牌校验，停用/解绑/过期都会返回 401/403，
     * 前端据此清理本地会话并隐藏业务入口。
     */
    @Anonymous
    @GetMapping("/profile")
    public Object profile(HttpServletRequest request)
    {
        String token = bearerToken(request);
        if (StringUtils.isEmpty(token))
        {
            throw new ArchiveBizException(4011, "尚未登录或缺少登录凭证，请重新登录");
        }
        Map<String, Object> profile = bindingService.profile(token, CLIENT_TYPE);
        return ok(profile);
    }

    /**
     * 从 Authorization 头提取 Bearer 令牌。
     *
     * @param request 当前请求
     * @return 令牌原文，缺失时返回空串
     */
    private static String bearerToken(HttpServletRequest request)
    {
        String header = request.getHeader("Authorization");
        if (StringUtils.isEmpty(header))
        {
            return "";
        }
        String prefix = "Bearer ";
        return header.regionMatches(true, 0, prefix, 0, prefix.length())
                ? StringUtils.strip(header.substring(prefix.length())) : "";
    }
}
