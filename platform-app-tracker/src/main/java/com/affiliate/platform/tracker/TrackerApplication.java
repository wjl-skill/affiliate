package com.affiliate.platform.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 联盟网盟与转化追踪数据面独立启动单元 (Affiliate Tracker Service Entry)
 * <p>
 * 纯无状态超低延迟边缘数据面：
 * 负责点击重定向 (/affiliate/click)、广告主 S2S 回传 (/affiliate/postback) 及 iOS SKAdNetwork 回传。
 * 返回前仅写入 Redis 频控与 Kafka 异步事件，不依赖 PostgreSQL 同步写热路径。
 */
@SpringBootApplication(scanBasePackages = {"com.affiliate.platform"})
public class TrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerApplication.class, args);
    }
}
