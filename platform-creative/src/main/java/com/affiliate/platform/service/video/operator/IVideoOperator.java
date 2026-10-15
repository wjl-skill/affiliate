package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;

import java.nio.file.Path;
import java.util.Map;

/**
 * 视频处理原子算子接口
 */
public interface IVideoOperator {

    /**
     * 算子类型
     */
    VideoProcessType operatorType();

    /**
     * 构建该算子的核心 FFmpeg 滤镜表达式或参数
     */
    String buildFilterCommand(VideoProbeResult probe, Map<String, Object> config);

    /**
     * 执行算子处理，在工作目录下生成输出目标文件
     */
    Path apply(Path inputPath, Map<String, Object> config, Path workDir) throws Exception;
}
