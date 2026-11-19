package com.ruoyi.common.utils.qrcode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.ruoyi.common.exception.ServiceException;

/**
 * 把二维码矩阵按比例放大并渲染成 PNG 字节。
 *
 * 只使用 JDK 自带的 {@code ImageIO} 与 {@code BufferedImage}，不依赖 zxing:javase。
 *
 * 注意：静区（二维码四周的空白边距）由 {@link QrCodeEncoder} 在编码时通过
 * {@code EncodeHintType.MARGIN} 交给 ZXing 处理，本类**不再额外加边距**，
 * 否则会出现双倍静区、图像无谓变大。
 *
 * 使用灰度色彩空间且不写 ICC 配置，避免部分扫码工具对带色彩配置的 PNG 解析异常。
 */
public final class QrCodePngWriter
{
    /** 单边像素上限，避免异常大的 scale 导致内存暴涨 */
    private static final int MAX_PIXELS_PER_SIDE = 4000;

    private QrCodePngWriter()
    {
    }

    /**
     * 渲染为 PNG。
     *
     * @param matrix 逐行矩阵，true 为深色
     * @param scale  每个模块的像素边长
     * @return PNG 字节
     */
    public static byte[] write(boolean[][] matrix, int scale)
    {
        if (matrix == null || matrix.length == 0 || matrix[0] == null || matrix[0].length == 0)
        {
            throw new ServiceException("二维码矩阵为空，无法生成图片");
        }
        int modules = matrix.length;
        int moduleScale = Math.max(1, scale);
        // 限制总边长，防止模块数 × 缩放倍率过大
        while (modules * moduleScale > MAX_PIXELS_PER_SIDE && moduleScale > 1)
        {
            moduleScale--;
        }
        int pixels = modules * moduleScale;

        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < pixels; y++)
        {
            for (int x = 0; x < pixels; x++)
            {
                // TYPE_BYTE_GRAY 下 0 为黑、255 为白，先整幅填白
                image.getRaster().setSample(x, y, 0, 255);
            }
        }
        for (int row = 0; row < modules; row++)
        {
            boolean[] line = matrix[row];
            for (int col = 0; col < modules; col++)
            {
                if (!line[col])
                {
                    continue;
                }
                int x0 = col * moduleScale;
                int y0 = row * moduleScale;
                for (int dy = 0; dy < moduleScale; dy++)
                {
                    for (int dx = 0; dx < moduleScale; dx++)
                    {
                        image.getRaster().setSample(x0 + dx, y0 + dy, 0, 0);
                    }
                }
            }
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("生成二维码图片失败");
        }
    }
}
