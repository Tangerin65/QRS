package com.ruoyi.web.controller.mobile;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.IArchiveBindingService;

/**
 * M12-B 小程序认证接口。
 *
 * 路径与成员3 的建议契约一致（doc/team/小程序公共部分接入说明.md）：
 *   POST /api/mobile/auth/login    微信免登：code → 未绑定 {bound:false}，已绑定返回会话
 *   POST /api/mobile/auth/logout   撤销 Bearer Token
 *   POST /api/mobile/account/bind  code + userNo + 账号口令 → 首次绑定并返回会话
 *
 * 说明：这些接口按 Anonymous 放行（小程序没有 Shiro 会话），鉴权完全由本控制器用
 * Bearer 令牌完成；因此每个方法内部都必须先校验令牌，不能依赖 Shiro 过滤器。
 *
 * @author member6
 */
@RestController
@RequestMapping("/api/mobile")
public class MobileAuthController extends MobileApiSupport
{
    /** 客户端类型，用于令牌归属以后区分小程序与 PC 扫码 */
    private static final String CLIENT_TYPE = "miniapp";

    private final IArchiveBindingService bindingService;

    public MobileAuthController(IArchiveBindingService bindingService)
    {
        this.bindingService = bindingService;
    }

    /**
     * 微信免登。
     *
     * 请求体：{"code": "wx.login 临时凭证"}
     * 返回：未绑定 {"bound":false}；已绑定会话对象（含 token/userId/userName/roleName/permissions）
     */
    @Anonymous
    @PostMapping("/auth/login")
    public Object login(@RequestBody(required = false) Map<String, String> body)
    {
        String code = body == null ? null : body.get("code");
        Map<String, Object> session = bindingService.login(code, CLIENT_TYPE);
        // 已绑定时 session 里带 token，未绑定时只有 bound=false
        return ok(session);
    }

    /**
     * 首次绑定。
     *
     * 请求体：{"code":"...", "userNo":"0003", "password":"..."}
     *
     * 关键规则 1：绑定必须校验账号口令，不允许凭用户编号任意绑定。
     * 注意：客户端提交的任何 openid 都不采信，OpenID 只由服务端用 code 换取。
     */
    @Anonymous
    @PostMapping("/account/bind")
    public Object bind(@RequestBody(required = false) Map<String, String> body)
    {
        if (body == null)
        {
            throw new ArchiveBizException(4001, "请提交绑定所需信息");
        }
        String code = body.get("code");
        String userNo = body.get("userNo");
        // 兼容两种字段名，前端主用 password；userPwd 是老版本兼容写法
        String password = StringUtils.isNotEmpty(body.get("password")) ? body.get("password") : body.get("userPwd");
        Map<String, Object> session = bindingService.bind(code, userNo, password, CLIENT_TYPE);
        return ok("绑定成功", session);
    }

    /**
     * 撤销令牌（主动退出）。
     *
     * 请求头：Authorization: Bearer <token>
     */
    @Anonymous
    @PostMapping("/auth/logout")
    public Object logout(HttpServletRequest request)
    {
        String token = bearerToken(request);
        // 令牌为空也返回成功：本地已退出，服务端无需失败告警
        if (StringUtils.isNotEmpty(token))
        {
            bindingService.logout(token);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("revoked", true);
        return ok("已退出登录", data);
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
