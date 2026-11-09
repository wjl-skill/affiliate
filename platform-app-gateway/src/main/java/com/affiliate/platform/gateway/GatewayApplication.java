package com.affiliate.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Affiliate Platform 统一微服务 API 网关启动入口 (Spring Cloud Gateway + Nacos)
 * <p>
 * 职责定位：
 * 1. 作为全集群唯一外部流量入口（默认端口 8080），基于 Nacos 服务发现 (`lb://`) 路由分发至：
 *    - {@code platform-app-tracker} (8081): 点击重定向、S2S 归因回传、SKAN 4.0 隐私归因；
 *    - {@code platform-app-rtb} (8082): OpenRTB 2.5 极速竞价、Win Notice、曝光追踪；
 *    - {@code platform-app-admin} (8083): 多租户管理、DSP/SSP/DMP/CDP 控制台、财务账本与 OLAP 报表；
 *    - {@code platform-app-worker} (8084): 虚拟线程高并发自动补量、Outbox 事件治理；
 * 2. 基于 Nacos 配置中心 (`affiliate-gateway-routes.yaml`) 实现路由规则秒级动态热更新；
 * 3. 统一在网关边缘执行全链路 `X-Trace-Id` 染色、租户/Token 鉴权前置拦截、滑动窗口限流与 OpenRTB 降级保护。
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.affiliate.platform.gateway",
                "com.affiliate.platform.nacos"
        },
        exclude = {
                DataSourceAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisReactiveAutoConfiguration.class
        }
)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
