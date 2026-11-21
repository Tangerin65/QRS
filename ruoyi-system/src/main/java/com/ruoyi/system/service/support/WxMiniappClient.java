package com.ruoyi.system.service.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ArchiveBizException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 微信小程序登录凭证校验（code2Session）客户端。
 *
 * 约定：
 *  1) 只有本类接触 AppSecret，且绝不把 AppSecret、session_key、原始响应体写进日志；
 *  2) 返回值中的 openid 是唯一可信身份来源，客户端提交的 openid 一律不采信；
 *  3) 微信侧错误码映射为本项目业务码，便于小程序端给出中文提示。
 *
 * 配置（application.yml 的 wx.miniapp 节点）：
 *   app-id / app-secret：微信小程序 AppID 与 AppSecret
 *   code2session-url：默认为微信官方地址，测试环境可指向本地桩服务
 *
 * @author member6
 */
@Component
public class WxMiniappClient
{
    private static final Logger log = LoggerFactory.getLogger(WxMiniappClient.class);

    /** 微信返回的会话密钥字段名，取用时小心，不落日志 */
    private static final String SESSION_KEY = "session_key";

    /** 连接与读取超时（毫秒）：微信接口在弱网下也不应长时间占住请求线程 */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private static final int READ_TIMEOUT_MS = 8000;

    @Value("${wx.miniapp.app-id:}")
    private String appId;

    @Value("${wx.miniapp.app-secret:}")
    private String appSecret;

    @Value("${wx.miniapp.code2session-url:https://api.weixin.qq.com/sns/jscode2session}")
    private String code2SessionUrl;

    /**
     * 是否已配置真实凭证。未配置时只允许在本地自测模式下使用桩实现。
     */
    public boolean isConfigured()
    {
        return StringUtils.isNotEmpty(appId) && StringUtils.isNotEmpty(appSecret);
    }

    /**
     * 用 wx.login 的临时凭证换取 openid。
     *
     * 注意：这里刻意不用 ruoyi 自带的 {@code HttpUtils.sendGet}，因为它在 INFO 级别
     * 会把完整 URL 和响应体写进日志，而 URL 里带 AppSecret、响应体里带 session_key。
     * 本项目改用下面的私有实现，只记录「调用成功/失败」与错误码，绝不落敏感值。
     *
     * @param code 微信临时登录凭证
     * @return 换取结果（openid、unionid、sessionKey）
     */
    public WxSession exchange(String code)
    {
        if (StringUtils.isEmpty(code))
        {
            throw new ArchiveBizException(4001, "微信登录凭证为空，请重新进入小程序");
        }
        if (!isConfigured())
        {
            throw new ArchiveBizException(5001, "微信小程序凭证未配置，请联系管理员设置 wx.miniapp.app-id 与 app-secret");
        }
        String query = "appid=" + encode(appId)
                + "&secret=" + encode(appSecret)
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        String body;
        try
        {
            body = getQuietly(code2SessionUrl + "?" + query);
        }
        catch (IOException | RuntimeException e)
        {
            // 只记录异常类型，绝不打印 URL 或参数
            log.error("调用微信 code2Session 失败：{}", e.getClass().getSimpleName());
            throw new ArchiveBizException(5002, "微信登录服务暂时不可用，请稍后重试");
        }
        JSONObject json;
        try
        {
            json = JSON.parseObject(body);
        }
        catch (RuntimeException e)
        {
            log.error("微信 code2Session 返回内容无法解析，长度={}", body == null ? 0 : body.length());
            throw new ArchiveBizException(5002, "微信登录服务返回异常，请稍后重试");
        }
        if (json == null)
        {
            throw new ArchiveBizException(5002, "微信登录服务返回异常，请稍后重试");
        }
        Integer errcode = json.getInteger("errcode");
        if (errcode != null && errcode != 0)
        {
            // errcode 是微信侧错误码，可以记录；errmsg 不含敏感信息
            log.warn("微信 code2Session 返回错误码 {}：{}", errcode, json.getString("errmsg"));
            throw new ArchiveBizException(mapErrCode(errcode), describeErrCode(errcode));
        }
        String openId = StringUtils.strip(json.getString("openid"));
        if (StringUtils.isEmpty(openId))
        {
            throw new ArchiveBizException(5002, "微信登录服务未返回身份标识，请重新进入小程序");
        }
        return new WxSession(openId, json.getString("unionid"), json.getString(SESSION_KEY));
    }

    /**
     * 静默 GET：不打印 URL、不打印响应体、不打印请求参数。
     *
     * @param url 完整地址（含凭证参数，禁止记录到日志）
     * @return 响应文本
     */
    private static String getQuietly(String url) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try
        {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null)
            {
                return "";
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    content.append(line);
                }
            }
            return content.toString();
        }
        finally
        {
            connection.disconnect();
        }
    }

    /**
     * URL 参数编码，避免 AppSecret 里的特殊字符破坏请求。
     */
    private static String encode(String value)
    {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 微信错误码 → 本项目业务码。
     *
     * 40029 code 无效、40163 code 已被使用、45011 频率限制、40226 高风险用户。
     */
    private static int mapErrCode(int errcode)
    {
        return switch (errcode)
        {
            case 40029 -> 4002;
            case 40163 -> 4003;
            case 45011 -> 4004;
            case 40226 -> 4005;
            default -> 5002;
        };
    }

    private static String describeErrCode(int errcode)
    {
        return switch (errcode)
        {
            case 40029 -> "微信登录凭证无效，请重新进入小程序";
            case 40163 -> "微信登录凭证已被使用，请重新进入小程序";
            case 45011 -> "操作过于频繁，请稍后重试";
            case 40226 -> "当前微信账号存在风险，已限制登录";
            default -> "微信登录服务返回异常（" + errcode + "），请稍后重试";
        };
    }

    /**
     * 换取结果。
     *
     * 注意：sessionKey 只在本进程内用于解密手机号等敏感数据，本项目暂不使用，
     * 因此不放入会话对象、不返回前端、不写日志。
     *
     * @param openId     微信 OpenID
     * @param unionId    微信 UnionID（可能为空）
     * @param sessionKey 会话密钥（不对外暴露）
     */
    public record WxSession(String openId, String unionId, String sessionKey)
    {
        /**
         * 对外可展示的摘要，避免把 sessionKey 带出去。
         */
        public Map<String, Object> describe()
        {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("openId", ArchiveCryptoUtils.mask(openId));
            summary.put("unionId", ArchiveCryptoUtils.mask(unionId));
            return summary;
        }
    }
}
