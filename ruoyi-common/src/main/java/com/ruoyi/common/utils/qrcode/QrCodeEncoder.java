package com.ruoyi.common.utils.qrcode;

import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ruoyi.common.exception.ServiceException;

/**
 * 二维码生成工具（基于 ZXing）。
 *
 * 为什么用 ZXing 而不是自己实现：二维码编码涉及 Reed-Solomon 纠错、分块交织、
 * 掩码择优等大量细节，成熟库（Apache-2.0）久经验证，可读性与可维护性都远好于自研实现。
 *
 * 本类只做两件事：
 *  1) 把文本编码成二维码矩阵（{@link #encodeText(String)}）；
 *  2) 把矩阵渲染成 PNG 字节（{@link #writePng(boolean[][])}）。
 *
 * 渲染直接用 JDK 自带的 ImageIO，因此只需要 ZXing 的 core 模块，
 * 不需要 zxing:javase（那样会额外引入 jcommander 与 jai-imageio 两个本项目用不到的依赖）。
 *
 * 安全约定：调用方只传入用户编号这类公开信息，不要把姓名、手机号、令牌编进二维码。
 */
public final class QrCodeEncoder
{
    /** 二维码四周静区模块数，ZXing 要求不小于 4，扫码工具依赖它定位 */
    private static final int QUIET_ZONE = 4;

    /** 默认每个模块的像素边长 */
    private static final int DEFAULT_SCALE = 8;

    /** 纠错等级：用户编号很短，M（约 15% 冗余）足够，且矩阵不会太大 */
    private static final ErrorCorrectionLevel ECC = ErrorCorrectionLevel.M;

    private QrCodeEncoder()
    {
    }

    /**
     * 把文本编码为二维码矩阵。
     *
     * @param text 二维码内容（本项目为纯文本用户编号，例如 0003）
     * @return 逐行的布尔矩阵，true 表示深色模块
     */
    public static boolean[][] encodeText(String text)
    {
        if (text == null || text.isEmpty())
        {
            throw new ServiceException("二维码内容不能为空");
        }
        // ZXing 的字符集提示必须显式指定，否则中文或多字节场景可能按 ISO-8859-1 处理
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ECC);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE);
        try
        {
            // 宽度传 0 表示「按内容自动选择版本」，避免小内容被撑成固定大图
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            return toArray(matrix);
        }
        catch (WriterException | IllegalArgumentException e)
        {
            throw new ServiceException("二维码生成失败，请检查编号内容是否过长");
        }
    }

    /**
     * 把矩阵渲染成 PNG 字节。
     *
     * @param matrix 逐行矩阵，true 为深色
     * @return PNG 字节
     */
    public static byte[] writePng(boolean[][] matrix)
    {
        return QrCodePngWriter.write(matrix, DEFAULT_SCALE);
    }

    /**
     * 生成二维码 PNG 的一步入口。
     *
     * @param text  二维码内容
     * @param scale 模块像素边长，小于等于 0 时用默认值
     * @return PNG 字节
     */
    public static byte[] toPng(String text, int scale)
    {
        return QrCodePngWriter.write(encodeText(text), scale > 0 ? scale : DEFAULT_SCALE);
    }

    /**
     * 生成 base64 data URI，便于前端直接内嵌到 img 的 src。
     *
     * @param text  二维码内容
     * @param scale 模块像素边长
     * @return 形如 data:image/png;base64,... 的字符串
     */
    public static String toBase64DataUri(String text, int scale)
    {
        return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(toPng(text, scale));
    }

    /**
     * BitMatrix 转二维布尔数组。
     *
     * ZXing 的 BitMatrix 已包含 margin（静区），所以这里直接整块复制，
     * 渲染阶段不再重复加边距，避免出现双倍静区。
     */
    private static boolean[][] toArray(BitMatrix matrix)
    {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                result[y][x] = matrix.get(x, y);
            }
        }
        return result;
    }
}
