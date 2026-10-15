package com.affiliate.platform.service.ai.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频模型生成器工厂与注册表 (Video Generator Factory)
 */
@Component
public class VideoGeneratorFactory {

    private static final Logger log = LoggerFactory.getLogger(VideoGeneratorFactory.class);

    private final Map<String, IVideoGenerator> generatorRegistry = new ConcurrentHashMap<>();

    public VideoGeneratorFactory(List<IVideoGenerator> generators) {
        if (generators != null) {
            for (IVideoGenerator generator : generators) {
                String code = generator.supportModelCode().toLowerCase().trim();
                generatorRegistry.put(code, generator);
                log.info("[VideoGeneratorFactory] Registered generator: modelCode={}, class={}",
                        code, generator.getClass().getSimpleName());
            }
        }
        log.info("[VideoGeneratorFactory] Total registered generators: {}", generatorRegistry.size());
    }

    public IVideoGenerator getGenerator(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode cannot be blank");
        }
        IVideoGenerator generator = generatorRegistry.get(modelCode.toLowerCase().trim());
        if (generator == null) {
            throw new IllegalArgumentException("No generator registered for modelCode: " + modelCode);
        }
        return generator;
    }

    public boolean hasGenerator(String modelCode) {
        return modelCode != null && generatorRegistry.containsKey(modelCode.toLowerCase().trim());
    }
}
