package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.VideoProcessType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频原子算子工厂注册中心
 */
@Component
public class VideoOperatorFactory {

    private final Map<VideoProcessType, IVideoOperator> operators = new ConcurrentHashMap<>();

    public VideoOperatorFactory(List<IVideoOperator> operatorList) {
        if (operatorList != null) {
            for (IVideoOperator op : operatorList) {
                operators.put(op.operatorType(), op);
            }
        }
    }

    public Optional<IVideoOperator> getOperator(VideoProcessType type) {
        return Optional.ofNullable(operators.get(type));
    }

    public IVideoOperator getRequiredOperator(VideoProcessType type) {
        return getOperator(type)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported video process operator type: " + type));
    }
}
