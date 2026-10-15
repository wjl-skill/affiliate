package com.affiliate.platform.domain.video;

/**
 * 画幅对齐缩放与黑边补齐数学计算结果
 */
public record ScalePaddingResult(
        int originalWidth,
        int originalHeight,
        int targetWidth,
        int targetHeight,
        double scaleFactor,
        int scaledWidth,
        int scaledHeight,
        int padX,
        int padY,
        boolean isExactMatch
) {
    public static ScalePaddingResult calculate(int srcW, int srcH, int targetW, int targetH) {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) {
            return new ScalePaddingResult(srcW, srcH, targetW, targetH, 1.0, targetW, targetH, 0, 0, false);
        }

        if (srcW == targetW && srcH == targetH) {
            return new ScalePaddingResult(srcW, srcH, targetW, targetH, 1.0, targetW, targetH, 0, 0, true);
        }

        double scaleX = (double) targetW / srcW;
        double scaleY = (double) targetH / srcH;
        double scale = Math.min(scaleX, scaleY);

        int scaledW = (int) Math.round(srcW * scale);
        int scaledH = (int) Math.round(srcH * scale);

        // 确保像素为偶数以便于大部分编码器 (h264)
        if (scaledW % 2 != 0) scaledW -= 1;
        if (scaledH % 2 != 0) scaledH -= 1;

        int padX = Math.max(0, (targetW - scaledW) / 2);
        int padY = Math.max(0, (targetH - scaledH) / 2);

        return new ScalePaddingResult(
                srcW, srcH, targetW, targetH, scale, scaledW, scaledH, padX, padY, false
        );
    }
}
