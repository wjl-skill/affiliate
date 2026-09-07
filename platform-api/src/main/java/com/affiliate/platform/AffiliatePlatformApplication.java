package com.affiliate.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 广告联盟聚合平台顶级 Spring Boot 引导启动类 (Affiliate Platform Application Entry)
 * <p>
 * 启动全套聚合微服务生态：
 * 包含统一 API 网关、多租户上下文、RTB 实时竞价引擎、DMP/CDP 受众画像、发件箱消息总线及财务计费中台。
 */
@SpringBootApplication
public class AffiliatePlatformApplication {

    /**
     * 应用程序主启动入口
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AffiliatePlatformApplication.class, args);
    }
}
