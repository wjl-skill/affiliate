package com.affiliate.platform.service.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供应商适配器注册工厂 (Video Provider Factory)
 */
@Component
public class VideoProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VideoProviderFactory.class);

    private final Map<String, AiVideoProviderAdapter> adapterRegistry = new ConcurrentHashMap<>();

    public VideoProviderFactory(List<AiVideoProviderAdapter> adapters) {
        if (adapters != null) {
            for (AiVideoProviderAdapter adapter : adapters) {
                String code = adapter.supportProviderCode().toUpperCase().trim();
                adapterRegistry.put(code, adapter);
                log.info("[VideoProviderFactory] Registered provider adapter: providerCode={}, class={}",
                        code, adapter.getClass().getSimpleName());
            }
        }
        log.info("[VideoProviderFactory] Total registered provider adapters: {}", adapterRegistry.size());
    }

    public AiVideoProviderAdapter getProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("providerCode cannot be blank");
        }
        AiVideoProviderAdapter adapter = adapterRegistry.get(providerCode.toUpperCase().trim());
        if (adapter == null) {
            throw new IllegalArgumentException("No provider adapter found for code: " + providerCode);
        }
        return adapter;
    }

    public boolean hasProvider(String providerCode) {
        return providerCode != null && adapterRegistry.containsKey(providerCode.toUpperCase().trim());
    }
}
