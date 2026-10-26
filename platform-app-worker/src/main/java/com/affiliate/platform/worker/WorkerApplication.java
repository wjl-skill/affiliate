package com.affiliate.platform.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步后台任务与事件中继 Worker 独立运行单元 (Worker & Background Tasks Entry)
 * <p>
 * 纯后台无外部流量业务 Worker：
 * 负责事务发件箱 (Outbox Relay) 定时投递、日终对账巡检、转化批量出账打款、
 * 素材生成任务异步轮询与流量智能补量调度。
 */
@SpringBootApplication(scanBasePackages = {"com.affiliate.platform"})
@EnableScheduling
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
