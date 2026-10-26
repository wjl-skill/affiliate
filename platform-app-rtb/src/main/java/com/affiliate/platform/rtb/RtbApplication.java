package com.affiliate.platform.rtb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 程序化广告 RTB / ADX 毫秒级竞价数据面独立启动单元 (RTB Bidder & ADX Engine Entry)
 * <p>
 * 超低延迟高性能计算单元：
 * 采用 OpenRTB 2.5 协议、ZGC 垃圾回收调优与分层内存倒排索引，
 * 承诺 P99 响应时间严格小于 80ms。
 */
@SpringBootApplication(scanBasePackages = {"com.affiliate.platform"})
public class RtbApplication {

    public static void main(String[] args) {
        SpringApplication.run(RtbApplication.class, args);
    }
}
