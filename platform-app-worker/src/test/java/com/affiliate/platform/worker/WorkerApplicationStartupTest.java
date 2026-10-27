package com.affiliate.platform.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = WorkerApplication.class)
@ActiveProfiles("pg")
class WorkerApplicationStartupTest {

    @Test
    @DisplayName("Worker 启动类入口与 Spring 容器可正常加载")
    void contextLoads() {
        assertNotNull(WorkerApplication.class);
    }
}
