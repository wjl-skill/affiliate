package com.affiliate.platform.service.video.probe;

import com.affiliate.platform.domain.video.VideoProbeResult;

import java.nio.file.Path;

/**
 * 媒体探针服务接口：提取音视频基础技术参数元数据
 */
public interface MediaProbeService {

    /**
     * 探针本地文件
     */
    VideoProbeResult probe(Path videoPath);

    /**
     * 探针在线 URL 或本地路径
     */
    VideoProbeResult probe(String videoUrlOrPath);

    /**
     * 根据分辨率计算标准画幅比例 (如 "9:16", "16:9", "1:1")
     */
    String resolveAspectRatio(int width, int height);
}
