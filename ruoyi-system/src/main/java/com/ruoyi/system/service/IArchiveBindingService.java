package com.ruoyi.system.service;

import java.util.Map;

/**
 * M12-B 微信绑定与小程序自动登录 服务层
 *
 * 安全边界（详细设计 5.3.12 / 模块拆分文档第 3 节）：
 *  1) OpenID 只由服务端用微信 code 换取，客户端提交的 openid 一律不采信；
 *  2) 会话令牌为服务端签发的不透明随机串，库里只存 SHA-256 摘要，原文只在签发时返回一次；
 *  3) 解绑或停用账号时 bind_version 递增，该账号的历史令牌全部立即失效；
 *  4) session_key 不返回客户端、不写日志。
 *
 * @author member6
 */
public interface IArchiveBindingService
{
    /**
     * 小程序登录：用 wx.login 的 code 换取身份，再按绑定关系返回会话。
     *
     * @param code       微信临时登录凭证
     * @param clientType 客户端类型，小程序固定 miniapp
     * @return 未绑定时 {"bound": false}；已绑定时返回会话对象
     */
    Map<String, Object> login(String code, String clientType);

    /**
     * 首次绑定：code + 用户编号，服务端换 OpenID 后建立绑定并签发会话。
     *
     * @param code       微信临时登录凭证
     * @param userNo     平台用户编号（纯文本，保留前导零）
     * @param accountPwd 平台账号口令，用于确认绑定人身份（绑定不是凭编号就能做）
     * @param clientType 客户端类型
     * @return 会话对象
     */
    Map<String, Object> bind(String code, String userNo, String accountPwd, String clientType);

    /**
     * 校验令牌并返回最新权限的会话对象。
     *
     * @param token      令牌原文（来自 Authorization: Bearer）
     * @param clientType 客户端类型
     * @return 会话对象
     */
    Map<String, Object> session(String token, String clientType);

    /**
     * 小程序个人中心资料：校验令牌后返回头像、基础资料与绑定状态。
     *
     * 只返回本账号可公开的基础字段（姓名、编号、角色、部门、绑定时间），
     * 不含 OpenID 原文、口令、盐等敏感内容。
     *
     * @param token      令牌原文
     * @param clientType 客户端类型
     * @return 个人资料视图
     */
    Map<String, Object> profile(String token, String clientType);

    /**
     * 撤销该令牌（主动退出）。
     *
     * @param token 令牌原文
     */
    void logout(String token);

    /**
     * 管理员解绑：递增绑定版本并撤销该账号全部令牌。
     *
     * @param userId   账号ID
     * @param operator 操作者
     * @return 是否解除了有效绑定
     */
    boolean unbindByUser(Long userId, String operator);

    /**
     * 账号停用时的即时失效：撤销全部令牌并递增绑定版本。
     *
     * @param userId 账号ID
     */
    void revokeForDisabledAccount(Long userId);

    /**
     * 查询账号绑定状态与绑定时间。
     *
     * @param userId 账号ID
     * @return 未绑定时返回 null，否则返回 openId 掩码、绑定时间、绑定版本
     */
    Map<String, Object> bindingStatus(Long userId);

    /**
     * 清理过期令牌（供定时任务或管理操作调用）。
     *
     * @return 清理条数
     */
    int purgeExpiredTokens();
}
