package com.ruoyi.system.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * M12 用到的摘要与随机数工具。
 *
 * 为什么单独一个类：令牌摘要、票据摘要、绑定版本都要求「同一算法、同一编码」，
 * 如果各处各写一遍，很容易出现一处用 Base64、一处用 hex，导致校验永远失败。
 *
 * @author member6
 */
public final class ArchiveCryptoUtils
{
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ArchiveCryptoUtils()
    {
    }

    /**
     * 生成指定字节数的随机令牌，用 base64url 无填充编码，可直接放进二维码或 Authorization 头。
     *
     * @param bytes 随机字节数，建议不小于 32（即 43 位字符）
     * @return 令牌明文
     */
    public static String randomToken(int bytes)
    {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /**
     * 计算 SHA-256 摘要并转为小写十六进制串。
     *
     * 数据库只保存摘要，即使库被拖走也无法直接反推出可用的令牌。
     *
     * @param value 原文
     * @return 64 位十六进制串
     */
    public static String sha256Hex(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("待摘要内容不能为空");
        }
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            char[] output = new char[hashed.length * 2];
            for (int i = 0; i < hashed.length; i++)
            {
                int unsigned = hashed[i] & 0xFF;
                output[i * 2] = HEX[unsigned >>> 4];
                output[i * 2 + 1] = HEX[unsigned & 0x0F];
            }
            return new String(output);
        }
        catch (NoSuchAlgorithmException e)
        {
            // JDK 必须提供 SHA-256，走到这里说明运行环境异常
            throw new IllegalStateException("当前运行环境缺少 SHA-256 支持", e);
        }
    }

    /**
     * 常量时间比较，避免通过响应时间推断摘要前缀。
     *
     * @param left  值
     * @param right 值
     * @return 相同返回 true
     */
    public static boolean constantTimeEquals(String left, String right)
    {
        if (left == null || right == null)
        {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 把 OpenID 之类较长标识做掩码，用于后台展示（如 oX7***9k），避免完整值出现在页面上。
     *
     * @param value 原值
     * @return 掩码后的值
     */
    public static String mask(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "";
        }
        if (value.length() <= 6)
        {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}
