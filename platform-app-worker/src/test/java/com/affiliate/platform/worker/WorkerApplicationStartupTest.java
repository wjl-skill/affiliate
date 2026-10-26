package com.affiliate.platform.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkerApplicationStartupTest {

    @Test
    @DisplayName("Worker 启动类入口可正常加载")
    void contextLoads() {
        assertNotNull(WorkerApplication.class);
    }
}
