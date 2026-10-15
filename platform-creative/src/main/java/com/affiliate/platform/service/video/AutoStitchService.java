package com.affiliate.platform.service.video;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.ScalePaddingResult;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;

/**
 * 画幅自适应与混剪计算服务
 */
public interface AutoStitchService {

    /**
     * 根据画幅比例获取标准目标分辨率尺寸
     */
    Dimension resolveTargetDimension(String aspectRatio);

    /**
     * 计算输入视频到目标分辨率的缩放与 Letterbox 补齐边距
     */
    ScalePaddingResult calculateScaleAndPad(int srcW, int srcH, int targetW, int targetH);

    /**
     * 生成片头素材组与主体素材组的笛卡尔积任务组合
     */
    List<Map<String, String>> generateCartesianPairs(List<String> introVideoUrls, List<String> bodyVideoUrls);

    /**
     * 构建 FFmpeg 画幅适配滤镜串
     */
    String buildAdaptFilter(int srcW, int srcH, int targetW, int targetH, AlignMode alignMode);
}
