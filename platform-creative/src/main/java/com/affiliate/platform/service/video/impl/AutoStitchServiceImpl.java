package com.affiliate.platform.service.video.impl;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.ScalePaddingResult;
import com.affiliate.platform.service.video.AutoStitchService;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 画幅自适应与混剪计算服务实现
 */
@Service
public class AutoStitchServiceImpl implements AutoStitchService {

    @Override
    public Dimension resolveTargetDimension(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return new Dimension(1080, 1920); // 默认 9:16
        }
        String normalized = aspectRatio.trim().replace("：", ":");
        return switch (normalized) {
            case "16:9" -> new Dimension(1920, 1080);
            case "1:1" -> new Dimension(1080, 1080);
            case "4:5" -> new Dimension(1080, 1350);
            case "3:4" -> new Dimension(1080, 1440);
            case "4:3" -> new Dimension(1440, 1080);
            case "9:16" -> new Dimension(1080, 1920);
            default -> new Dimension(1080, 1920);
        };
    }

    @Override
    public ScalePaddingResult calculateScaleAndPad(int srcW, int srcH, int targetW, int targetH) {
        return ScalePaddingResult.calculate(srcW, srcH, targetW, targetH);
    }

    @Override
    public List<Map<String, String>> generateCartesianPairs(List<String> introVideoUrls, List<String> bodyVideoUrls) {
        List<Map<String, String>> pairs = new ArrayList<>();
        if (introVideoUrls == null || bodyVideoUrls == null || introVideoUrls.isEmpty() || bodyVideoUrls.isEmpty()) {
            return pairs;
        }

        for (String intro : introVideoUrls) {
            for (String body : bodyVideoUrls) {
                pairs.add(Map.of(
                        "introVideoUrl", intro,
                        "bodyVideoUrl", body
                ));
            }
        }
        return pairs;
    }

    @Override
    public String buildAdaptFilter(int srcW, int srcH, int targetW, int targetH, AlignMode alignMode) {
        AlignMode mode = alignMode != null ? alignMode : AlignMode.LETTERBOX;
        return switch (mode) {
            case CROP -> String.format("scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d",
                    targetW, targetH, targetW, targetH);
            case SCALE -> String.format("scale=%d:%d", targetW, targetH);
            case LETTERBOX -> String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black",
                    targetW, targetH, targetW, targetH);
        };
    }
}
