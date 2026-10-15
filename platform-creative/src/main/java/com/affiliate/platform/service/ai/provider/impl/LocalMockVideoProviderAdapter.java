package com.affiliate.platform.service.ai.provider.impl;

import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.dto.ProviderQueryResult;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitRequest;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地 Mock 供应商适配器 (用于开发、集成测试与离线环境联调)
 */
@Component
public class LocalMockVideoProviderAdapter implements AiVideoProviderAdapter {

    private final Map<String, AtomicInteger> pollCounters = new ConcurrentHashMap<>();

    @Override
    public String supportProviderCode() {
        return "LOCAL_MOCK";
    }

    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        String remoteId = "mock_remote_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        pollCounters.put(remoteId, new AtomicInteger(0));
        return ProviderSubmitResult.of(remoteId);
    }

    @Override
    public ProviderQueryResult queryTask(String remoteTaskId) {
        AtomicInteger counter = pollCounters.get(remoteTaskId);
        if (counter == null) {
            return ProviderQueryResult.failed(remoteTaskId, "TASK_NOT_FOUND", "Mock task not found");
        }

        int count = counter.incrementAndGet();
        // 第一次轮询返回 PROCESSING，第二次轮询即返回 SUCCEEDED
        if (count < 2) {
            return ProviderQueryResult.processing(remoteTaskId);
        }

        String fakeVideoUrl = "https://cdn.affiliate.com/mock/generated_video_" + remoteTaskId + ".mp4";
        return ProviderQueryResult.succeeded(remoteTaskId, fakeVideoUrl, BigDecimal.valueOf(5.0));
    }

    @Override
    public boolean cancelTask(String remoteTaskId) {
        pollCounters.remove(remoteTaskId);
        return true;
    }
}
