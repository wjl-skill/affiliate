package com.affiliate.platform.config;

import com.affiliate.platform.geo.IpLocationResolver;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * IP2Location 自动装配与配置注入组件
 * <p>
 * 读取 application.yml 中的 app.ip2location (或 ip2location) 配置项，
 * 动态配置 IpLocationResolver 二进制库路径与工作开关。
 */
@Configuration
public class IpLocationConfig {

    private static final Logger log = LoggerFactory.getLogger(IpLocationConfig.class);

    @Value("${app.ip2location.enabled:${ip2location.enabled:true}}")
    private boolean enabled;

    @Value("${app.ip2location.bin-path:${ip2location.bin-path:}}")
    private String binPath;

    @Value("${app.ip2location.cache-mode:${ip2location.cache-mode:MEMORY_MAPPED}}")
    private String cacheMode;

    @PostConstruct
    public void init() {
        if (binPath != null && !binPath.isBlank()) {
            log.info("Configuring IP2Location with external binPath=[{}], mode=[{}], enabled=[{}]", binPath, cacheMode, enabled);
            IpLocationResolver.configure(binPath, enabled);
        } else {
            log.info("IP2Location bin-path is not explicitly set in application configuration, using automatic scan and fallback mode.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBinPath() {
        return binPath;
    }

    public String getCacheMode() {
        return cacheMode;
    }
}
